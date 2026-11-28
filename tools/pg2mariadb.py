#!/usr/bin/env python3
"""pg2mariadb.py — convert a PostgreSQL dump to MariaDB-compatible SQL.

Ymax backup-only mirror tool. The MariaDB mirror is a data safety net;
it is never the app's primary database (primary = PostgreSQL 16).

Usage:
  pg2mariadb.py <pg_dump.sql> <out.sql>

Input formats supported (plain text pg_dump output):
  - schema-only dump          (pg_dump --schema-only)
  - full dump with --inserts  (pg_dump --inserts; --copy not supported)
"""
import re, sys

def strip_casts(line: str) -> str:
    # ::character varying (two-word) first, then ::<single_word> casts
    line = re.sub(r"::\s*character\s+varying\b", "", line, flags=re.I)
    line = re.sub(r"::\s*\w+(\[\d*\])?", "", line, flags=re.I)
    return line

def convert_table_body(line: str) -> str:
    """Convert a single CREATE TABLE column or constraint definition line."""
    line = strip_casts(line)
    # multi-word types first
    line = re.sub(r"\bTIMESTAMP WITH TIME ZONE\b", "DATETIME", line, flags=re.I)
    line = re.sub(r"\bTIMESTAMP WITHOUT TIME ZONE\b", "DATETIME", line, flags=re.I)
    line = re.sub(r"\bTIMESTAMPTZ\b", "DATETIME", line, flags=re.I)
    line = re.sub(r"\bDOUBLE PRECISION\b", "DOUBLE", line, flags=re.I)
    line = re.sub(r"\bCHARACTER VARYING\b", "VARCHAR", line, flags=re.I)
    line = re.sub(r"\bCHARACTER\b", "CHAR", line, flags=re.I)
    line = re.sub(r"\bTIMESTAMP\b", "DATETIME", line, flags=re.I)
    line = re.sub(r"\bBOOLEAN\b", "TINYINT(1)", line, flags=re.I)
    # PG array types -> TEXT (bracketed)
    line = re.sub(r"\b(TEXT|INT|INTEGER|BIGINT|REAL|DOUBLE|FLOAT|DATETIME|DATE|CHAR|JSON|BYTEA|SERIAL|BIGSERIAL)\[\d*\]", "TEXT", line, flags=re.I)
    line = re.sub(r"\bSERIAL\b", "INT", line, flags=re.I)
    line = re.sub(r"\bBIGSERIAL\b", "BIGINT", line, flags=re.I)
    # NOT NULL DEFERRABLE / INITIALLY IMMEDIATE
    line = re.sub(r"\bNOT NULL\s+DEFERRABLE\b", "NOT NULL", line, flags=re.I)
    line = re.sub(r"\bINITIALLY\s+IMMEDIATE\b", "", line, flags=re.I)
    # remove nextval() defaults (replaced by AUTO_INCREMENT on PK col)
    line = re.sub(r"\bDEFAULT\s+nextval\([^)]*\)", "", line, flags=re.I)
    return line

def convert_generic(line: str) -> str:
    line = strip_casts(line)
    line = line.replace("public.", "")
    return line

def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <pg_dump.sql> <out.sql>", file=sys.stderr)
        sys.exit(2)

    infile, outfile = sys.argv[1], sys.argv[2]
    text = open(infile, encoding="utf-8", errors="replace").read()
    lines = text.splitlines(keepends=True)

    # --- Pass 0: table -> PK columns that are seq-owned ---
    seq_pk_cols = {}  # table -> list of pk col names that are seq-owned
    nextval_map = {}  # col (in any table) that had SET DEFAULT nextval
    for m in re.finditer(
        r"ALTER TABLE\s+(?:ONLY\s+)?(?:\w+\.)?(\w+)\s+ALTER COLUMN\s+(\w+)\s+SET DEFAULT nextval\(",
        text, re.I
    ):
        nextval_map[m.group(2)] = None
        seq_pk_cols.setdefault(m.group(1), []).append(m.group(2))

    for m in re.finditer(
        r"ALTER TABLE\s+(?:ONLY\s+)?(?:\w+\.)?(\w+)\s+ADD CONSTRAINT\s+\S+\s+PRIMARY KEY\s*\(([^)]*)\)",
        text, re.I
    ):
        tbl = m.group(1)
        pks = [c.strip() for c in m.group(2).split(",")]
        cols = [c for c in pks if c in nextval_map]
        if cols:
            seq_pk_cols[tbl] = cols

    out = []
    i = 0
    n = len(lines)

    while i < n:
        line = lines[i]
        s = line.strip()

        # --- skip plpgsql functions ---
        if re.match(r"^CREATE (OR REPLACE )?FUNCTION\b", s, re.I):
            i += 1
            while i < n and not lines[i].strip().startswith("$$;"):
                i += 1
            i += 1
            continue

        # --- skip PG-specific DDL ---
        if re.match(r"^(ALTER FUNCTION|CREATE TRIGGER|CREATE EXTENSION|ALTER TABLE \S+ OWNER TO|ALTER SEQUENCE|CREATE SEQUENCE|ALTER DATABASE|CREATE OR REPLACE FUNCTION|ALTER TABLE \S+ ENABLE TRIGGER)\b", s, re.I):
            i += 1
            continue

        # --- COPY blocks: reject ---
        if s.startswith("COPY ") and "FROM stdin" in s:
            sys.exit("COPY blocks not supported. Use: pg_dump --inserts (not default COPY format).")

        # --- ALTER COLUMN SET DEFAULT nextval: drop ---
        if "SET DEFAULT nextval" in s:
            i += 1
            continue

        # --- CREATE TABLE ---
        mt = re.match(r"^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+(?:\.\w+)?)\s*\(\s*$", line, re.I)
        if mt:
            tbl = mt.group(1).split(".")[-1]
            out.append(convert_generic(line))
            j = i + 1
            depth = 1
            body = []
            while j < n and depth > 0:
                l = lines[j]
                depth += l.count("(") - l.count(")")
                if depth == 0:
                    break
                cs = l.strip()
                if re.match(r"^(PRIMARY KEY|CONSTRAINT|FOREIGN KEY|UNIQUE|CHECK)\b", cs, re.I):
                    body.append(convert_table_body(l))
                else:
                    body.append(convert_table_body(l))
                j += 1
            # patch AUTO_INCREMENT on seq-owned PK columns
            patched = []
            if tbl in seq_pk_cols:
                patched = []
                for bl in body:
                    cs = bl.strip()
                    m2 = re.match(rf"^(\s*)({'|'.join(re.escape(c) for c in seq_pk_cols[tbl])})(\s+\w+(?:\(\d+\))?)(\s+NOT NULL)(\s*,?\s*)$", cs, re.I)
                    if m2:
                        patched.append(f"{m2.group(1)}{m2.group(2)}{m2.group(3)}{m2.group(4)} AUTO_INCREMENT{m2.group(5)}\n")
                    else:
                        patched.append(bl)
                out.extend(patched)
            else:
                out.extend(body)
            out.append(");\n")
            i = j + 1
            continue

        # --- CREATE INDEX: strip USING btree, schema qualifier ---
        mi = re.match(r"^\s*CREATE\s+(UNIQUE\s+)?INDEX\s+(\S+)\s+ON\s+", s, re.I)
        if mi:
            rest = s[mi.start(2):]
            rest = re.sub(r"\s*USING\s+(btree|hash|gin|gist|spgist)\b", "", rest, flags=re.I)
            out.append(convert_generic(rest + "\n"))
            i += 1
            continue

        # --- plain line ---
        out.append(convert_generic(line))
        i += 1

    result = "".join(out)
    result = re.sub(r"\bUSING\s+btree\b", "", result, flags=re.I)

    with open(outfile, "w", encoding="utf-8") as f:
        f.write(f"-- Generated by pg2mariadb.py from: {infile}\n")
        f.write("-- Ymax: MariaDB is backup-only; primary is PostgreSQL 16.\n")
        f.write("SET FOREIGN_KEY_CHECKS=0;\n")
        f.write(result)
        f.write("SET FOREIGN_KEY_CHECKS=1;\n")

    print(f"OK: wrote {outfile}")

if __name__ == "__main__":
    main()
