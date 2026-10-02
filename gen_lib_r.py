#!/usr/bin/env python3
"""Generate library R.java files from overlay-merged aapt2 R.txt.

For each AAR with resources, collect its resource (type, name) set from the
AAR's res/ dir, look up the final merged IDs in the app's R.txt, and emit
<pkg>/R.java with matching fields (values must equal the merged table,
because the prebuilt classes.jar bytecode reads them via field access).
"""
import os, re, sys, xml.etree.ElementTree as ET

OUT = os.environ.get("ZYNE_OUT", "/tmp/zyne-iptv-out")
PROJ = os.environ.get("ZYNE_PROJ", os.path.expanduser("~/workspace/zyne-iptv"))

# ---- final merged symbols: (type, name) -> id / styleable children ----
ids = {}            # (type, name) -> "0x..."
styleable_arrays = {}  # name -> [ids]
styleable_idx = {}     # (name, attr) -> index
rtype, rname = None, None
with open(os.path.join(OUT, "R.txt")) as f:
    for line in f:
        line = line.strip()
        m = re.match(r"^int (\w+) (\S+) (0x[0-9a-fA-F]+)$", line)
        if m:
            ids[(m.group(1), m.group(2))] = m.group(3)
            continue
        m = re.match(r"^int\[\] (\w+) (\S+) \{(.*)\}$", line)
        if m:
            styleable_arrays[m.group(2)] = [x.strip() for x in m.group(3).split(",") if x.strip()]
            continue
        m = re.match(r"^int (\w+) (\S+)_(\S+) (\d+)$", line)
        if m and m.group(1) == "styleable":
            styleable_idx[(m.group(2), m.group(3))] = m.group(4)

# ---- per-AAR resource names ----
def aar_resources(resdir):
    """Return dict type -> set(names)."""
    out = {}
    def add(t, n):
        out.setdefault(t, set()).add(n)
    values_types = {"string", "color", "dimen", "id", "integer", "bool",
                    "attr", "style", "plurals", "array", "fraction", "styleable"}
    for root, dirs, files in os.walk(resdir):
        base = os.path.basename(root)
        if base.startswith("values"):
            for fn in files:
                if not fn.endswith(".xml"):
                    continue
                try:
                    tree = ET.parse(os.path.join(root, fn))
                except Exception:
                    continue
                for el in tree.getroot().iter():
                    tag = el.tag
                    if "}" in tag:
                        tag = tag.split("}", 1)[1]
                    if tag == "declare-styleable":
                        sn = el.get("name")
                        if sn:
                            add("styleable", sn)
                            for a in el.iter("attr"):
                                an = a.get("name")
                                if an and ":" not in an:
                                    add("styleable_child", sn + "_" + an.split("/")[-1])
                        continue
                    if tag == "item":
                        # generic form: <item name="foo" type="id"/>
                        it = el.get("type")
                        n = el.get("name")
                        if it in values_types and n and ":" not in n:
                            add(it, n)
                        continue
                    if tag in values_types and el.get("name"):
                        add(tag, el.get("name"))
        else:
            # resource dir like drawable, layout, mipmap, xml, raw, anim, font, color
            rtype = base.split("-")[0]
            if rtype in ("drawable", "layout", "mipmap", "anim", "xml",
                         "raw", "font", "color", "menu"):
                for fn in files:
                    n = fn
                    # strip extensions, incl. .9.png
                    n = re.sub(r"\.9\.png$", "", n)
                    n = re.sub(r"\.(png|jpg|jpeg|gif|webp|xml)$", "", n)
                    if n:
                        add(rtype, n)
    return out

generated = []
with open(os.path.join(OUT, "aar_pkgs.txt")) as f:
    pkgs = [l.strip().split("|") for l in f if l.strip()]

for aar_name, pkg in pkgs:
    resdir = os.path.join(OUT, "aar", aar_name, "res")
    res = aar_resources(resdir)
    # inner class -> list of (field, value_expr)
    inners = {}
    for t, names in res.items():
        if t == "styleable_child":
            continue
        if t == "styleable":
            for sn in sorted(names):
                arr = styleable_arrays.get(sn)
                if not arr:
                    continue
                inners.setdefault("styleable", []).append(
                    (sn, "{ " + ", ".join(arr) + " }"))
                # children as indices
                for key, idx in sorted(styleable_idx.items()):
                    if key[0] == sn:
                        inners["styleable"].append((sn + "_" + key[1], idx))
            continue
        fields = []
        for n in sorted(names):
            v = ids.get((t, n))
            if v:
                fields.append((n, v))
        if fields:
            inners[t] = fields
    if not inners:
        continue
    lines = ["package %s;" % pkg, "", "public final class R {"]
    for inner in sorted(inners):
        lines.append("  public static final class %s {" % inner)
        for fname, val in inners[inner]:
            if inner == "styleable" and val.startswith("{"):
                lines.append("    public static int[] %s = %s;" % (fname, val))
            else:
                lines.append("    public static int %s = %s;" % (fname, val))
        lines.append("  }")
    lines.append("}")
    destdir = os.path.join(OUT, "gen-lib-r", *pkg.split("."))
    os.makedirs(destdir, exist_ok=True)
    with open(os.path.join(destdir, "R.java"), "w") as f:
        f.write("\n".join(lines) + "\n")
    generated.append(pkg)

print("generated R for: %s" % ", ".join(generated))
