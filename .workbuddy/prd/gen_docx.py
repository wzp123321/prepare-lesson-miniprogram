# -*- coding: utf-8 -*-
"""Generate a valid .docx for the 排课系统 PRD using only the Python stdlib."""
import zipfile, datetime, os

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

OUT = r"D:/workspace/private/collect/prepare-lesson-miniprogram/备课排课管理系统PRD.docx"

def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;"))

def run(text, bold=False, color=None, size=None, font=None):
    rpr = ""
    if bold: rpr += "<w:b/>"
    if color: rpr += '<w:color w:val="%s"/>' % color
    if size: rpr += '<w:sz w:val="%d"/><w:szCs w:val="%d"/>' % (size, size)
    if font:
        rpr += '<w:rFonts w:ascii="%s" w:eastAsia="%s" w:hAnsi="%s"/>' % (font, font, font)
    rpr = "<w:rPr>%s</w:rPr>" % rpr if rpr else ""
    return '<w:r>%s<w:t xml:space="preserve">%s</w:t></w:r>' % (rpr, esc(text))

def para(text="", style=None, bold=False, shade=None, indent=None, color=None, size=None, font=None):
    ppr = ""
    if style: ppr += '<w:pStyle w:val="%s"/>' % style
    if shade: ppr += '<w:shd w:val="clear" w:color="auto" w:fill="%s"/>' % shade
    if indent: ppr += '<w:ind w:left="%d"/>' % indent
    ppr = "<w:pPr>%s</w:pPr>" % ppr if ppr else ""
    body = run(text, bold=bold, color=color, size=size, font=font) if text else ""
    return "<w:p>%s%s</w:p>" % (ppr, body)

def heading(text, level):
    return para(text, style="Heading%d" % level)

def title(text):
    return para(text, style="Title")

def bullet(text):
    return para("\u2022  " + text, indent=360)

def table(rows):
    n = max(len(r) for r in rows)
    total = 9000
    col_w = total // n
    grid = "".join('<w:gridCol w:w="%d"/>' % col_w for _ in range(n))
    borders = ("<w:tblBorders>" +
               "".join('<w:%s w:val="single" w:sz="4" w:space="0" w:color="9DA9B5"/>' % e
                       for e in ["top", "left", "bottom", "right", "insideH", "insideV"]) +
               "</w:tblBorders>")
    tblpr = ('<w:tblPr><w:tblW w:w="0" w:type="auto"/>%s'
             '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="0" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr>' % borders)
    out = ["<w:tbl>", tblpr, "<w:tblGrid>%s</w:tblGrid>" % grid]
    for ri, row in enumerate(rows):
        is_head = (ri == 0)
        out.append("<w:tr>")
        if is_head:
            out.append('<w:trPr><w:tblHeader/></w:trPr>')
        for ci in range(n):
            cell = row[ci] if ci < len(row) else ""
            shd = '<w:shd w:val="clear" w:color="auto" w:fill="EEF3FB"/>' if is_head else ""
            tcpr = '<w:tcPr><w:tcW w:w="%d" w:type="dxa"/>%s</w:tcPr>' % (col_w, shd)
            r = run(cell, bold=is_head, font="\u5b8b\u4f53")
            out.append("<w:tc>%s<w:p>%s</w:p></w:tc>" % (tcpr, r))
        out.append("</w:tr>")
    out.append("</w:tbl>")
    # spacer paragraph after table
    out.append('<w:p><w:pPr><w:spacing w:after="60"/></w:pPr></w:p>')
    return "".join(out)

# ---------- PRD content ----------
C = []
C.append(title("\u5907\u8bfe\u6392\u8bfe\u7ba1\u7406\u7cfb\u7edf \u00b7 \u4ea7\u54c1\u9700\u6c42\u6587\u6863\uff08PRD\uff09"))
C.append(para("\u7248\u672c v0.1 \uff5c \u65e5\u671f 2026-09-30 \uff5c \u72b6\u6001\uff1a\u9700\u6c42\u8bc4\u5ba1\u7a3f \uff5c \u4f5c\u8005\uff1a\u963f\u67a2\uff08\u4ea7\u54c1/\u7814\u53d1\uff09", color="666666", size=20))
C.append(para("\u4e00\u53e5\u8bdd\u5b9a\u4f4d\uff1a\u9762\u5411\u72ec\u7acb\u5c0f\u5b66/\u521d\u4e2d\u8bed\u6587\u6559\u5e08\u7684 Web \u6392\u8bfe\u7ba1\u7406\u5de5\u5177\uff0c\u7528\u4e8e\u6bcf\u6708\u6392\u8bfe\u3001\u5b9e\u65f6\u67e5\u770b\u5f53\u6708\u4e0a\u8bfe\u60c5\u51b5\u3001\u7ba1\u7406\u4e0a\u8bfe\u72b6\u6001\uff08\u6b63\u5e38 / \u987a\u5ef6 / \u5df2\u8865 / \u4f5c\u5e9f\uff09\uff0c\u5e76\u81ea\u52a8\u8ddf\u8e2a\u300c\u5f85\u8865\u8bfe\u300d\u9632\u6b62\u6f0f\u8865\u3002",
             shade="F0F6FF"))
C.append(heading("1. \u6587\u6863\u4fe1\u606f", 2))
C.append(table([
    ["\u9879", "\u5185\u5bb9"],
    ["\u6587\u6863\u540d\u79f0", "\u5907\u8bfe\u6392\u8bfe\u7ba1\u7406\u7cfb\u7edf PRD"],
    ["\u7248\u672c", "v0.1"],
    ["\u65e5\u671f", "2026-09-30"],
    ["\u72b6\u6001", "\u9700\u6c42\u8bc4\u5ba1\u7a3f\uff08\u5f85\u7528\u6237\u786e\u8ba4\u540e\u8fdb\u5165\u5f00\u53d1\uff09"],
    ["\u9002\u7528\u8303\u56f4", "\u7ba1\u7406\u540e\u53f0 + \u524d\u53f0\uff08Web \u7aef\uff0c\u79fb\u52a8\u7aef\u540e\u7eed\uff09"],
]))
C.append(heading("2. \u9879\u76ee\u80cc\u666f\u4e0e\u76ee\u6807", 2))
C.append(heading("2.1 \u80cc\u666f", 3))
C.append(para("\u7528\u6237\u4e3a\u72ec\u7acb\u8bed\u6587\u6559\u5e08\uff0c\u9762\u5411\u5c0f\u5b66\u3001\u521d\u4e2d\u5b66\u751f\uff0c\u6bcf\u6708\u8fdb\u884c\u6392\u8bfe\uff08\u6bcf\u5929\u54ea\u4e9b\u65f6\u6bb5\u7ed9\u54ea\u4e2a\u5b66\u751f\u4e0a\u8bfe\uff09\u3002\u5f53\u524d\u7528\u7eb8\u8d28\u8868\u683c\u8bb0\u5f55\uff0c\u5b58\u5728\u4ee5\u4e0b\u75db\u70b9\uff1a"))
C.append(bullet("\u5b66\u751f\u8bf7\u5047 / \u6559\u5e08\u8bf7\u5047\u540e\uff0c\u987a\u5ef6\u8bfe\u7a0b\u5bb9\u6613\u5fd8\u8bb0\u8865\uff0c\u5bfc\u81f4\u300c\u6b20\u8bfe\u6f0f\u8865\u300d\uff1b"))
C.append(bullet("\u6bcf\u6708\u5b9e\u9645\u4e0a\u8bfe\u60c5\u51b5\u65e0\u6cd5\u5b9e\u65f6\u6c47\u603b\uff0c\u96be\u4ee5\u4e00\u773c\u770b\u6e05\u300c\u54ea\u4e9b\u4e0a\u4e86\u3001\u54ea\u4e9b\u987a\u5ef6\u4e86\u3001\u4ec0\u4e48\u539f\u56e0\u300d\uff1b"))
C.append(bullet("\u7eb8\u8d28\u8bb0\u5f55\u96be\u4ee5\u7edf\u8ba1\u6536\u5165\uff08\u5b9e\u9645\u4e0a\u8bfe\u8282\u6570 \u00d7 \u8bfe\u7a0b\u4ef7\u683c\uff09\u3002"))
C.append(heading("2.2 \u76ee\u6807", 3))
C.append(bullet("\u63d0\u4f9b\u4e00\u4e2a Web \u7cfb\u7edf\uff0c\u96c6\u4e2d\u7ba1\u7406\u5b66\u751f / \u65f6\u95f4\u6bb5 / \u8bfe\u7a0b / \u6392\u8bfe\uff1b"))
C.append(bullet("\u5b9e\u65f6\u67e5\u770b\u6bcf\u4e2a\u6708\u7684\u4e0a\u8bfe\u60c5\u51b5\uff0c\u5e76\u7ba1\u7406\u6bcf\u8282\u8bfe\u7684\u4e0a\u8bfe\u72b6\u6001\uff1b"))
C.append(bullet("\u81ea\u52a8\u6c47\u603b\u5f85\u8865\u8bfe\uff0c\u95ed\u73af\u300c\u987a\u5ef6 \u2192 \u5b89\u6392\u8865\u8bfe \u2192 \u5df2\u8865\u300d\uff1b"))
C.append(bullet("\u6309\u6708\u7edf\u8ba1\u4e0a\u8bfe\u60c5\u51b5\u4e0e\u6536\u5165\u3002"))
C.append(heading("2.3 \u6210\u529f\u6807\u51c6", 3))
C.append(bullet("\u6392\u8bfe\u53ef\u5728\u5f53\u6708\u7f51\u683c\u4e2d\u62d6\u62fd\u5b8c\u6210\uff1b"))
C.append(bullet("\u524d\u53f0\u9996\u9875\u80fd\u5c55\u793a\u5f85\u8865\u8bfe\u5217\u8868\uff08\u65e0\u5219\u4e0d\u5c55\u793a\uff09\uff1b"))
C.append(bullet("\u5f53\u6708\u8bfe\u7a0b\u8868\u53ef\u6807\u8bb0\u72b6\u6001\u5e76\u8bb0\u5f55\u987a\u5ef6\u539f\u56e0\uff1b"))
C.append(bullet("\u6708\u6536\u5165 = \uff08\u6b63\u5e38\u4e0a\u8bfe + \u5df2\u8865\uff09\u8282\u6570 \u00d7 \u5b66\u751f\u4ef7\u683c\uff0c\u53ef\u6309\u6708\u67e5\u8be2\u3002"))

C.append(heading("3. \u7528\u6237\u4e0e\u89d2\u8272", 2))
C.append(table([
    ["\u89d2\u8272", "\u8bf4\u660e", "\u6743\u9650"],
    ["\u72ec\u7acb\u6559\u5e08\uff08\u552f\u4e00\u7528\u6237\uff09", "\u7cfb\u7edf\u7684\u4f7f\u7528\u8005\u4e0e\u7ba1\u7406\u8005", "\u5168\u90e8\u529f\u80fd"],
]))
C.append(para("\u672c\u671f\u6682\u4e0d\u505a\u767b\u5f55 / \u591a\u89d2\u8272\uff1b\u540e\u7eed\u5982\u9700\u5206\u4eab\u7ed9\u5b66\u751f\u5bb6\u957f\uff0c\u518d\u52a0\u8d26\u53f7\u4f53\u7cfb\u3002", color="666666", size=20))

C.append(heading("4. \u603b\u4f53\u529f\u80fd\u67b6\u6784", 2))
C.append(para("\u7cfb\u7edf\u5206\u4e3a\u7ba1\u7406\u540e\u53f0\uff08\u5f55\uff09\u4e0e\u524d\u53f0\uff08\u770b\uff09\u4e24\u5927\u90e8\u5206\uff0c\u6570\u636e\u540c\u6e90\u3002"))
C.append(table([
    ["\u7aef", "\u6a21\u5757", "\u6838\u5fc3\u804c\u8d23"],
    ["\u7ba1\u7406\u540e\u53f0", "\u5b66\u751f\u7ba1\u7406", "\u7ef4\u62a4\u5b66\u751f\u6863\u6848"],
    ["\u7ba1\u7406\u540e\u53f0", "\u65f6\u95f4\u6bb5\u7ba1\u7406", "\u7ef4\u62a4\u4e0a\u8bfe\u65f6\u6bb5\uff08\u8d77\u6b62\u65f6\u95f4\u3001\u6392\u5e8f\uff09"],
    ["\u7ba1\u7406\u540e\u53f0", "\u8bfe\u7a0b\u7ba1\u7406", "\u6bcf\u4e2a\u5b66\u751f\u4e00\u95e8\u8bed\u6587\u8bfe\uff08\u4ef7\u683c\u3001\u5907\u6ce8\uff09"],
    ["\u7ba1\u7406\u540e\u53f0", "\u6392\u8bfe\u7cfb\u7edf", "\u5f53\u6708\u7f51\u683c\u62d6\u62fd\u6392\u8bfe\u3001\u6309\u5b66\u751f\u51fa\u56fe\u3001\u4fdd\u5b58\u65f6\u5173\u95ed\u4e0a\u6708\u987a\u5ef6"],
    ["\u7ba1\u7406\u540e\u53f0", "\u5b57\u5178\u7ba1\u7406", "\u7ef4\u62a4\u5e74\u7ea7\u3001\u987a\u5ef6\u539f\u56e0\u7b49\u5b57\u5178"],
    ["\u524d\u53f0", "\u4eca\u65e5\u89c6\u56fe", "\u843d\u5730\u9875\uff0c\u4eca\u65e5\u8bfe\u7a0b + \u5feb\u901f\u6807\u8bb0\u72b6\u6001"],
    ["\u524d\u53f0", "\u6570\u636e\u603b\u89c8", "\u6309\u6708\u67e5\u8be2\u3001\u5386\u53f2\u6708\u672a\u4e0a\u8bfe\u8868 + \u539f\u56e0\u3001\u6708\u6536\u5165\u3001\u987a\u5ef6\u5206\u7c7b"],
    ["\u524d\u53f0", "\u5f53\u6708\u8bfe\u7a0b\u8868", "\u590d\u7528\u7f51\u683c\uff0c\u7ba1\u7406\u6bcf\u8282\u8bfe\u72b6\u6001"],
    ["\u524d\u53f0", "\u5f85\u8865\u8bfe\u9996\u9875", "\u5c55\u793a\u5f85\u8865\u8bfe\u5217\u8868\uff0c\u53ef\u300c\u5df2\u5b89\u6392\u8fdb\u672c\u6708\u8bfe\u7a0b\u300d\u5173\u95ed"],
]))

C.append(heading("5. \u529f\u80fd\u8be6\u8ff0", 2))
C.append(heading("5.1 \u5b66\u751f\u7ba1\u7406", 3))
C.append(para("\u5b57\u6bb5\uff1a\u59d3\u540d\u3001\u5e74\u7ea7\uff08\u5c0f/\u521d + \u5e74\u7ea7\uff09\u3001\u7535\u8bdd\u3001\u5bb6\u957f\u5fae\u4fe1\u3001\u5bb6\u5ead\u5730\u5740\u3001\u8bfe\u7a0b\u4ef7\u683c\u3001\u72b6\u6001\uff08\u5728\u8bfb / \u6682\u505c\uff09\u3001\u4e13\u5c5e\u989c\u8272\u3001\u5907\u6ce8\u3002"))
C.append(bullet("\u5217\u8868 + \u641c\u7d22\uff08\u6309\u59d3\u540d / \u5e74\u7ea7\uff09\uff1b"))
C.append(bullet("\u8be6\u60c5\u53ef\u67e5\u770b\u8be5\u751f\u6240\u6709\u8bfe\u7a0b\u4e0e\u5f85\u8865\u8bfe\u6570\uff1b"))
C.append(bullet("\u5220\u9664\u4fdd\u62a4\uff1a\u6709\u672a\u7ed3\u8bfe\u7a0b / \u672a\u6765\u6392\u8bfe\u65f6\u7981\u6b62\u786c\u5220\uff0c\u4ec5\u53ef\u7f6e\u300c\u6682\u505c\u300d\u5f52\u6863\u3002"))
C.append(heading("5.2 \u65f6\u95f4\u6bb5\u7ba1\u7406", 3))
C.append(para("\u5b57\u6bb5\uff1a\u5f00\u59cb\u65f6\u95f4\u3001\u7ed3\u675f\u65f6\u95f4\u3001\u6392\u5e8f\u3002"))
C.append(bullet("\u589e\u5220\u6539 + \u62d6\u62fd\u6392\u5e8f\uff1b"))
C.append(bullet("\u6821\u9a8c\u4e0d\u80fd\u91cd\u53e0\uff08\u5982 08:00\u201310:00 \u4e0e 09:00\u201311:00 \u51b2\u7a81\u987b\u62e6\u622a\uff09\uff1b"))
C.append(bullet("\u4f5c\u4e3a\u6392\u8bfe\u7f51\u683c\u7684\u300c\u5217\u300d\u3002"))
C.append(heading("5.3 \u8bfe\u7a0b\u7ba1\u7406", 3))
C.append(para("\u56e0\u53ea\u4e0a\u8bed\u6587\uff0c\u8bfe\u7a0b = \u67d0\u4e2a\u5b66\u751f\u7684\u4e00\u95e8\u8bed\u6587\u8bfe\u3002\u5b57\u6bb5\uff1a\u5f52\u5c5e\u5b66\u751f\u3001\u79d1\u76ee\uff08\u56fa\u5b9a\u8bed\u6587\uff09\u3001\u5355\u4ef7\uff08\u53d6\u81ea\u5b66\u751f\u8bfe\u7a0b\u4ef7\u683c\uff09\u3001\u5907\u6ce8\u3002"))
C.append(bullet("\u5217\u8868\u6309\u5b66\u751f\u5206\u7ec4\uff1b\u53ef\u505c\u7528\uff08\u5b66\u751f\u4e0d\u5b66\u5219\u4fdd\u7559\u5386\u53f2\uff09\uff1b"))
C.append(bullet("\u4ef7\u683c\u4fee\u6539\u4ec5\u5f71\u54cd\u4e4b\u540e\u6392\u8bfe\uff0c\u4e0d\u56de\u6eaf\u5386\u53f2\u3002"))
C.append(heading("5.4 \u6392\u8bfe\u7cfb\u7edf", 3))
C.append(para("\u4ea4\u4e92\uff1a\u5de6\u4fa7\u5b66\u751f\u5217\u8868\uff08\u53ef\u62d6\u62fd\uff09\uff1b\u4e2d\u95f4\u5f53\u6708\u7f51\u683c\u2014\u2014\u9996\u5217\u4e3a\u65e5\u671f\uff08\u5f53\u6708\u6bcf\u4e00\u5929\u4e00\u884c\uff09\uff0c\u9996\u884c\u4e3a\u65f6\u95f4\u6bb5\uff08\u6bcf\u5217\u4e00\u65f6\u6bb5\uff09\uff1b\u4e2d\u95f4\u5355\u5143\u683c\u4e3a\u53ef\u62d6\u62fd\u533a\u57df\u3002"))
C.append(bullet("\u628a\u5b66\u751f\u62d6\u5165\u67d0\u5355\u5143\u683c \u2192 \u751f\u6210\u4e00\u6761\u5f53\u5929\u8be5\u65f6\u6bb5\u7684\u8bfe\uff1b\u6bcf\u683c\u4ec5\u80fd\u62d6\u5165 1 \u540d\u5b66\u751f\uff081 \u5bf9 1\uff09\uff1b"))
C.append(bullet("\u6392\u8bfe\u53ea\u80fd\u6392\u5f53\u6708\uff1b\u5386\u53f2\u6708\u540e\u53f0\u4e0d\u53ef\u6392\uff1b"))
C.append(bullet("\u4fdd\u5b58\u65f6\uff0c\u540e\u7aef\u5148\u67e5\u8be2\u4e0a\u6708\u6240\u6709\u300c\u987a\u5ef6\u300d\u8bfe\uff0c\u6709\u5219\u968f\u4fdd\u5b58\u4e00\u5e76\u5173\u95ed\uff08\u89c6\u4e3a\u5df2\u5b89\u6392\u8fdb\u672c\u6708\uff09\uff1b"))
C.append(bullet("\u4fdd\u5b58\u540e\u652f\u6301\u6309\u5b66\u751f\u751f\u6210\u5f53\u6708\u8bfe\u8868\u56fe\u7247\uff08PNG\uff0c\u7528\u4e8e\u7559\u5b58 / \u53d1\u5bb6\u957f\uff09\uff1b"))
C.append(bullet("\u7f51\u683c\u65e0\u8bfe\u663e\u793a\u7a7a\u767d\uff0c\u5468\u672b\u4e0d\u7f6e\u7070\u3002"))
C.append(heading("5.5 \u8bf7\u5047\u4e0e\u987a\u5ef6", 3))
C.append(para("\u5728\u5f53\u6708\u8bfe\u7a0b\u8868\uff08\u524d\u53f0\uff09\u6216\u6392\u8bfe\u6761\u76ee\u4e0a\uff0c\u53ef\u6807\u8bb0\u67d0\u8282\u8bfe\u72b6\u6001\u4e3a\u987a\u5ef6\uff0c\u5f39\u7a97\u5fc5\u586b\uff1a"))
C.append(bullet("\u8bf7\u5047\u65b9\uff1a\u5b66\u751f\u8bf7\u5047 / \u8001\u5e08\u8bf7\u5047\uff1b"))
C.append(bullet("\u987a\u5ef6\u539f\u56e0\uff08\u81ea\u7531\u6587\u672c\uff0c\u8ba1\u5165\u5b57\u5178\u300c\u987a\u5ef6\u539f\u56e0\u300d\u53ef\u9009\u9884\u8bbe\uff09\u3002"))
C.append(para("\u6807\u8bb0\u987a\u5ef6\u540e\uff0c\u8be5\u8bfe\u8fdb\u5165\u5f85\u8865\u8bfe\u6c60\u3002"))
C.append(heading("5.6 \u8865\u8bfe\u95ed\u73af\u4e0e\u5f85\u8865\u8bfe", 3))
C.append(bullet("\u5f85\u8865\u8bfe\u5b9a\u4e49\uff1a\u4efb\u610f\u6708\u4efd\u4e2d\u72b6\u6001 = \u987a\u5ef6\u4e14\u672a\u5173\u95ed\u7684\u8bfe\uff1b"))
C.append(bullet("\u524d\u53f0\u9996\u9875\u5b9e\u65f6\u67e5\u8be2\u6240\u6709\u5f85\u8865\u8bfe\uff1a\u6709\u5219\u5217\u8868\u5c55\u793a\uff0c\u65e0\u5219\u4e0d\u5c55\u793a\uff1b"))
C.append(bullet("\u6bcf\u6761\u5f85\u8865\u8bfe\u53ef\u64cd\u4f5c\u300c\u5df2\u5b89\u6392\u8fdb\u672c\u6708\u8bfe\u7a0b\u300d \u2192 \u5173\u95ed\u8be5\u5f85\u8865\uff08\u72b6\u6001\u7f6e\u5df2\u8865 / \u5df2\u5b89\u6392\uff09\uff1b\u5b9e\u9645\u8865\u8bfe\u7531\u6559\u5e08\u5728\u6392\u8bfe\u7f51\u683c\u628a\u8be5\u751f\u62d6\u5230\u67d0\u5929\u67d0\u65f6\u6bb5\u5b8c\u6210\uff1b"))
C.append(bullet("\u8de8\u6708\u6d88\u9664\uff1a\u4e0a\u6708\u6ca1\u4e0a\u7684\u8bfe\uff0c\u672c\u6708\uff08\u6216\u540e\u7eed\u6708\uff09\u6392\u5165\u8bfe\u7a0b\u8868\u540e\uff0c\u7ecf\u5173\u95ed\u52a8\u4f5c\u5373\u4e0d\u518d\u662f\u5f85\u8865\uff1b"))
C.append(bullet("\u8865\u8bfe\u65e5\u671f\u53ef\u843d\u4efb\u610f\u6708\uff08\u5b58\u5b57\u6bb5\uff09\uff0c\u4e0d\u989d\u5916\u5360\u7528\u7f51\u683c\u5355\u5143\u3002"))
C.append(heading("5.7 \u5b57\u5178\u7ba1\u7406", 3))
C.append(para("\u7ef4\u62a4\u5b57\u5178\u8868\uff0c\u672c\u671f\u5305\u542b\uff1a\u5e74\u7ea7\u3001\u987a\u5ef6\u539f\u56e0\u9884\u8bbe\u3002\u4f9b\u4e0b\u62c9\u4e0e\u9009\u9879\u590d\u7528\uff1b\u540e\u7eed\u53ef\u6269\u5c55\u5907\u6ce8\u6807\u7b7e\u7b49\u3002"))
C.append(heading("5.8 \u6570\u636e\u603b\u89c8", 3))
C.append(bullet("\u6309\u6708\u67e5\u8be2\uff08\u542b\u5386\u53f2\u6708\uff09\uff1b"))
C.append(bullet("\u5f53\u6708 / \u5386\u53f2\u6708\u5c55\u793a\uff1a\u6392\u8bfe\u603b\u6570\u3001\u5df2\u4e0a\u3001\u987a\u5ef6\uff08\u5b66\u751f\u8bf7\u5047 / \u8001\u5e08\u8bf7\u5047\u5404\u51e0\u6b21\uff09\u3001\u5df2\u8865\u3001\u4f5c\u5e9f\u3001\u5f85\u8865\uff1b"))
C.append(bullet("\u5386\u53f2\u6708\u989d\u5916\u5c55\u793a\u672a\u4e0a\u8bfe\u8bfe\u7a0b\u8868 + \u539f\u56e0\uff1b"))
C.append(bullet("\u6708\u6536\u5165 = \uff08\u6b63\u5e38\u4e0a\u8bfe + \u5df2\u8865\uff09\u8282\u6570 \u00d7 \u8be5\u751f\u8bfe\u7a0b\u4ef7\u683c\uff1b\u987a\u5ef6\u672a\u8865 / \u4f5c\u5e9f\u4e0d\u8ba1\u5165\uff1b"))
C.append(bullet("\u987a\u5ef6\u539f\u56e0\u5206\u7c7b\u7edf\u8ba1\uff1a\u5b66\u751f\u8bf7\u5047 vs \u8001\u5e08\u8bf7\u5047\u6b21\u6570\u3002"))
C.append(heading("5.9 \u4eca\u65e5\u89c6\u56fe\uff08\u843d\u5730\u9875\uff09", 3))
C.append(bullet("\u8fdb\u5165\u7cfb\u7edf\u9ed8\u8ba4\u9875\uff1b\u5c55\u793a\u4eca\u5929\u7684\u8bfe\u7a0b\u6e05\u5355\uff1b"))
C.append(bullet("\u6bcf\u8282\u8bfe\u4e00\u952e\u6807\u8bb0\uff1a\u672a\u4e0a \u2192 \u6b63\u5e38\u4e0a\u8bfe / \u987a\u5ef6\uff08\u586b\u539f\u56e0\uff09\uff1b"))
C.append(bullet("\u987a\u5e26\u63d0\u793a\u300c\u4eca\u65e5\u5f85\u8865\u8bfe\u7a0b\u300d\u3002"))
C.append(heading("5.10 \u5f53\u6708\u8bfe\u7a0b\u8868\uff08\u72b6\u6001\u7ba1\u7406\uff09", 3))
C.append(para("\u590d\u7528\u6392\u8bfe\u540c\u6b3e\u6708\u7f51\u683c\uff0c\u6bcf\u683c\u70b9\u5f00\u53ef\u7ba1\u7406\u72b6\u6001\uff1a\u6b63\u5e38\u4e0a\u8bfe / \u987a\u5ef6\uff08\u586b\u539f\u56e0\uff09/ \u5df2\u8865 / \u4f5c\u5e9f\uff1b\u987a\u5ef6\u6807\u7ea2\u5e76\u663e\u793a\u539f\u56e0\u3002"))

C.append(heading("6. \u6570\u636e\u6a21\u578b", 2))
C.append(heading("6.1 \u5b9e\u4f53\u5173\u7cfb", 3))
C.append(para("Student \u2500\u2500< Course\uff08\u67d0\u5b66\u751f\u7684\u8bed\u6587\u8bfe\uff09\u2500\u2500< Lesson\uff08\u5177\u4f53\u67d0\u5929\u67d0\u8282\uff09", shade="F3F4F6"))
C.append(para("TimeSlot \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518\uff08Lesson \u5f15\u7528 TimeSlot \u51b3\u5b9a\u65f6\u95f4\uff09", shade="F3F4F6"))
C.append(heading("6.2 \u8868\u7ed3\u6784", 3))
C.append(para("student\uff08\u5b66\u751f\uff09", bold=True))
C.append(table([
    ["\u5b57\u6bb5", "\u7c7b\u578b", "\u8bf4\u660e"],
    ["id", "bigint", "\u4e3b\u952e"],
    ["name", "varchar", "\u59d3\u540d"],
    ["grade", "varchar", "\u5e74\u7ea7\uff08\u5c0f\u4e09 / \u521d\u4e00\uff0c\u5b57\u5178\uff09"],
    ["phone", "varchar", "\u7535\u8bdd"],
    ["parent_wechat", "varchar", "\u5bb6\u957f\u5fae\u4fe1"],
    ["address", "varchar", "\u5bb6\u5ead\u5730\u5740"],
    ["price", "decimal", "\u8bfe\u7a0b\u4ef7\u683c\uff08\u6bcf\u8282\uff09"],
    ["status", "tinyint", "1 \u5728\u8bfb / 0 \u6682\u505c"],
    ["color", "varchar", "\u8bfe\u8868\u4e13\u5c5e\u8272"],
    ["remark", "varchar", "\u5907\u6ce8"],
]))
C.append(para("time_slot\uff08\u65f6\u95f4\u6bb5\uff09", bold=True))
C.append(table([
    ["\u5b57\u6bb5", "\u7c7b\u578b", "\u8bf4\u660e"],
    ["id", "bigint", "\u4e3b\u952e"],
    ["start_time", "time", "\u5f00\u59cb\u65f6\u95f4"],
    ["end_time", "time", "\u7ed3\u675f\u65f6\u95f4"],
    ["sort_order", "int", "\u6392\u5e8f"],
]))
C.append(para("course\uff08\u8bfe\u7a0b\uff09", bold=True))
C.append(table([
    ["\u5b57\u6bb5", "\u7c7b\u578b", "\u8bf4\u660e"],
    ["id", "bigint", "\u4e3b\u952e"],
    ["student_id", "bigint", "\u5b66\u751f"],
    ["subject", "varchar", "\u79d1\u76ee\uff08\u56fa\u5b9a\uff1a\u8bed\u6587\uff09"],
    ["price", "decimal", "\u5355\u4ef7\uff08\u53d6\u81ea\u5b66\u751f\uff09"],
    ["remark", "varchar", "\u5907\u6ce8"],
    ["enabled", "tinyint", "\u662f\u5426\u542f\u7528"],
]))
C.append(para("lesson\uff08\u6392\u8bfe / \u4e0a\u8bfe\u8bb0\u5f55\uff09", bold=True))
C.append(table([
    ["\u5b57\u6bb5", "\u7c7b\u578b", "\u8bf4\u660e"],
    ["id", "bigint", "\u4e3b\u952e"],
    ["student_id", "bigint", "\u5b66\u751f"],
    ["course_id", "bigint", "\u8bfe\u7a0b"],
    ["time_slot_id", "bigint", "\u65f6\u95f4\u6bb5"],
    ["lesson_date", "date", "\u4e0a\u8bfe\u65e5\u671f\uff08\u4ec5\u5f53\u6708\uff09"],
    ["status", "varchar", "\u672a\u4e0a / \u6b63\u5e38\u4e0a\u8bfe / \u987a\u5ef6 / \u5df2\u8865 / \u4f5c\u5e9f"],
    ["absent_by", "varchar", "\u987a\u5ef6\u65f6\uff1a\u5b66\u751f / \u8001\u5e08"],
    ["absent_reason", "varchar", "\u987a\u5ef6\u539f\u56e0"],
    ["makeup_date", "date", "\u8865\u8bfe\u65e5\u671f\uff08\u53ef\u8de8\u6708\uff09"],
    ["makeup_slot_id", "bigint", "\u8865\u8bfe\u65f6\u6bb5"],
    ["makeup_status", "varchar", "\u5f85\u8865 / \u5df2\u7ea6 / \u5df2\u8865 / \u5df2\u5b89\u6392"],
    ["created_at / updated_at", "datetime", "\u65f6\u95f4\u6233"],
]))
C.append(para("dict\uff08\u5b57\u5178\u8868\uff09", bold=True))
C.append(table([
    ["\u5b57\u6bb5", "\u7c7b\u578b", "\u8bf4\u660e"],
    ["id", "bigint", "\u4e3b\u952e"],
    ["type", "varchar", "\u5b57\u5178\u7c7b\u578b\uff08grade / absence_reason\uff09"],
    ["code", "varchar", "\u7f16\u7801"],
    ["label", "varchar", "\u663e\u793a\u503c"],
    ["sort_order", "int", "\u6392\u5e8f"],
]))

C.append(heading("7. \u8bfe\u7a0b\u72b6\u6001\u673a", 2))
C.append(table([
    ["\u72b6\u6001", "\u542b\u4e49", "\u53ef\u8fbe\u4e0b\u4e00\u72b6\u6001"],
    ["\u672a\u4e0a", "\u5df2\u6392\u8bfe\u3001\u5c1a\u672a\u53d1\u751f", "\u6b63\u5e38\u4e0a\u8bfe / \u987a\u5ef6 / \u4f5c\u5e9f"],
    ["\u6b63\u5e38\u4e0a\u8bfe", "\u5df2\u6309\u65f6\u5b8c\u6210", "\uff08\u7ec8\u6001\uff09"],
    ["\u987a\u5ef6", "\u5b66\u751f / \u8001\u5e08\u8bf7\u5047\uff0c\u9700\u8865\u8bfe", "\u5df2\u8865 / \u4f5c\u5e9f\uff08\u7ecf\u300c\u5df2\u5b89\u6392\u8fdb\u672c\u6708\u8bfe\u7a0b\u300d\u5173\u95ed\uff09"],
    ["\u5df2\u8865", "\u8865\u8bfe\u5df2\u5b8c\u6210", "\uff08\u7ec8\u6001\uff09"],
    ["\u4f5c\u5e9f", "\u4e0d\u8865\uff08\u5982\u957f\u671f\u505c\u8bfe\uff09", "\uff08\u7ec8\u6001\uff09"],
]))
C.append(para("\u672a\u6765\u65e5\u671f\u663e\u793a\u300c\u672a\u4e0a\u300d\uff1b\u8fc7\u53bb\u65e5\u671f\u7531\u6559\u5e08\u5728\u4eca\u65e5\u89c6\u56fe / \u5f53\u6708\u8bfe\u7a0b\u8868\u6807\u8bb0\u3002\u987a\u5ef6 \u2192 \u5b89\u6392\u8865\u8bfe \u2192 \u5df2\u8865 \u6784\u6210\u8865\u8bfe\u95ed\u73af\u3002", color="666666", size=20))

C.append(heading("8. \u975e\u529f\u80fd\u9700\u6c42", 2))
C.append(bullet("\u5e73\u53f0\uff1aWeb \u4f18\u5148\uff08\u684c\u9762\u62d6\u62fd\u6392\u8bfe\uff09\uff1b\u79fb\u52a8\u7aef\u529f\u80fd\u7a33\u5b9a\u540e\u5b9e\u73b0\uff0c\u8bef\u65f6\u6392\u8bfe\u6539\u7528\u300c\u70b9\u683c\u9009\u5b66\u751f\u300d\uff1b"))
C.append(bullet("\u767b\u5f55\uff1a\u672c\u671f\u65e0\u767b\u5f55\uff1b"))
C.append(bullet("\u5b58\u50a8\uff1a\u540e\u7aef\u6570\u636e\u5e93\uff08MySQL\uff09\uff0c\u6570\u636e\u6301\u4e45\u5316\uff1b"))
C.append(bullet("\u6027\u80fd\uff1a\u5355\u6708\u8bfe\u8868 < 1000 \u5355\u5143\u683c\uff0c\u4ea4\u4e92\u5373\u65f6\uff1b"))
C.append(bullet("\u53ef\u5907\u4efd\uff1a\u6570\u636e\u5e93\u5b9a\u671f\u5907\u4efd\uff08\u7531\u90e8\u7f72\u4fa7\u4fdd\u969c\uff09\u3002"))

C.append(heading("9. \u6280\u672f\u67b6\u6784", 2))
C.append(table([
    ["\u5c42", "\u6280\u672f", "\u8bf4\u660e"],
    ["\u524d\u7aef", "Vue 3 + Vite + TypeScript", "\u7ba1\u7406\u540e\u53f0 + \u524d\u53f0\uff1b\u6392\u8bfe\u62d6\u62fd\u7f51\u683c\uff1b\u6309\u5b66\u751f\u51fa\u56fe\uff08html2canvas\uff09"],
    ["\u540e\u7aef", "Spring Boot", "REST API\uff1b\u6392\u8bfe\u4fdd\u5b58\u65f6\u5173\u95ed\u4e0a\u6708\u987a\u5ef6\uff1b\u6309\u6708\u7edf\u8ba1"],
    ["\u6570\u636e\u5e93", "MySQL\uff0847.116.35.76\uff09", "\u5b66\u751f / \u65f6\u95f4\u6bb5 / \u8bfe\u7a0b / \u6392\u8bfe / \u5b57\u5178 \u7b49\u8868"],
    ["\u90e8\u7f72", "\u540e\u7aef\u90e8\u7f72\u4e8e\u670d\u52a1\u5668\uff0c\u524d\u7aef\u6784\u5efa\u540e\u7531\u540e\u7aef\u6258\u7ba1\u6216 Nginx", "Web \u8bbf\u95ee"],
]))

C.append(heading("10. \u8303\u56f4\u4e0e\u6392\u671f", 2))
C.append(heading("10.1 MVP\uff08\u672c\u671f\uff09", 3))
C.append(bullet("\u5b66\u751f / \u65f6\u95f4\u6bb5 / \u8bfe\u7a0b / \u5b57\u5178 \u7ba1\u7406\uff1b"))
C.append(bullet("\u6392\u8bfe\u7cfb\u7edf\uff08\u5f53\u6708\u7f51\u683c\u62d6\u62fd\u3001\u6bcf\u683c 1 \u4eba\u3001\u4fdd\u5b58\u5173\u4e0a\u6708\u987a\u5ef6\u3001\u6309\u5b66\u751f\u51fa\u56fe\uff09\uff1b"))
C.append(bullet("\u8bf7\u5047\u987a\u5ef6\uff08\u586b\u539f\u56e0\uff09\u3001\u8865\u8bfe\u95ed\u73af\u3001\u5f85\u8865\u8bfe\u9996\u9875\uff1b"))
C.append(bullet("\u4eca\u65e5\u89c6\u56fe\u3001\u6570\u636e\u603b\u89c8\uff08\u6309\u6708\u3001\u5386\u53f2\u6708\u672a\u4e0a\u8bfe\u8868 + \u539f\u56e0\u3001\u6708\u6536\u5165\u3001\u987a\u5ef6\u5206\u7c7b\uff09\u3001\u5f53\u6708\u8bfe\u7a0b\u8868\u72b6\u6001\u7ba1\u7406\u3002"))
C.append(heading("10.2 \u540e\u7eed\uff08\u672c\u671f\u4e0d\u505a\uff09", 3))
C.append(bullet("\u79fb\u52a8\u7aef\u9002\u914d\uff1b"))
C.append(bullet("\u767b\u5f55 / \u591a\u89d2\u8272\uff08\u5206\u4eab\u5b66\u751f\u5bb6\u957f\uff09\uff1b"))
C.append(bullet("\u5047\u671f / \u505c\u8bfe\u65e5\u8bbe\u7f6e\uff1b"))
C.append(bullet("\u6570\u636e\u5907\u4efd\u5bfc\u51fa / \u5bfc\u5165\u3001\u62a5\u8868\u6253\u5370\u5bfc\u51fa\uff08\u6309\u5b66\u751f\u51fa\u56fe\u4fdd\u7559\uff09\uff1b"))
C.append(bullet("\u6536\u8d39 / \u8bfe\u65f6\u5305\u4f59\u989d\u3002"))

C.append(heading("11. \u98ce\u9669\u4e0e\u5f85\u786e\u8ba4", 2))
C.append(bullet("MySQL \u8fde\u63a5\u4fe1\u606f\uff08\u5e93\u540d / \u8d26\u53f7 / \u5bc6\u7801\uff09\u7531\u7528\u6237\u540e\u7eed\u8865\u5145\uff1b"))
C.append(bullet("\u300c\u5df2\u5b89\u6392\u8fdb\u672c\u6708\u8bfe\u7a0b\u300d\u5173\u95ed\u5f85\u8865\u65f6\uff0c\u662f\u5426\u9700\u81ea\u52a8\u5728\u7f51\u683c\u751f\u6210\u8865\u8bfe\u5355\u5143 \u2014\u2014 \u672c\u671f\u91c7\u7528\u300c\u4ec5\u5173\u95ed + \u6559\u5e08\u624b\u52a8\u62d6\u5165\u8865\u8bfe\u300d\uff1b"))
C.append(bullet("\u987a\u5ef6\u8de8\u591a\u4e2a\u6708\uff08\u65e9\u4e8e\u4e0a\u6708\uff09\u7684\u5f85\u8865\uff0c\u4f9d\u8d56\u9996\u9875\u624b\u52a8\u5173\u95ed\u6216\u5bf9\u5e94\u6708\u4efd\u4fdd\u5b58\u65f6\u5173\u95ed\uff1b"))
C.append(bullet("\u6708\u6536\u5165\u53e3\u5f84\u4ee5\u300c\u6b63\u5e38\u4e0a\u8bfe + \u5df2\u8865\u300d\u8ba1\uff0c\u987a\u5ef6\u672a\u8865 / \u4f5c\u5e9f\u4e0d\u8ba1\uff0c\u5f85\u7528\u6237\u6700\u7ec8\u786e\u8ba4\u3002"))

body = "".join(C)
sectpr = ('<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
          '<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" '
          'w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>')
document_xml = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<w:document xmlns:w="%s"><w:body>%s%s</w:body></w:document>'
) % (W, body, sectpr)

styles_xml = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<w:styles xmlns:w="%s">'
    '<w:docDefaults><w:rPrDefault><w:rPr>'
    '<w:rFonts w:ascii="\u5b8b\u4f53" w:eastAsia="\u5b8b\u4f53" w:hAnsi="\u5b8b\u4f53"/>'
    '<w:sz w:val="21"/><w:szCs w:val="21"/></w:rPr></w:rPrDefault>'
    '<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="312" w:lineRule="auto"/></w:pPr></w:pPrDefault>'
    '</w:docDefaults>'
    '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>'
    '<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/>'
    '<w:pPr><w:spacing w:before="240" w:after="240"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="\u9ed1\u4f53" w:eastAsia="\u9ed1\u4f53" w:hAnsi="\u9ed1\u4f53"/><w:b/><w:color w:val="1F6FEB"/><w:sz w:val="36"/><w:szCs w:val="36"/></w:rPr></w:style>'
    '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>'
    '<w:pPr><w:keepNext/><w:spacing w:before="280" w:after="120"/><w:outlineLvl w:val="0"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="\u9ed1\u4f53" w:eastAsia="\u9ed1\u4f53" w:hAnsi="\u9ed1\u4f53"/><w:b/><w:color w:val="0B3D91"/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>'
    '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/>'
    '<w:pPr><w:keepNext/><w:spacing w:before="220" w:after="100"/><w:outlineLvl w:val="1"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="\u9ed1\u4f53" w:eastAsia="\u9ed1\u4f53" w:hAnsi="\u9ed1\u4f53"/><w:b/><w:color w:val="134075"/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr></w:style>'
    '<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/>'
    '<w:pPr><w:keepNext/><w:spacing w:before="160" w:after="80"/><w:outlineLvl w:val="2"/></w:pPr>'
    '<w:rPr><w:rFonts w:ascii="\u9ed1\u4f53" w:eastAsia="\u9ed1\u4f53" w:hAnsi="\u9ed1\u4f53"/><w:b/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:style>'
    '</w:styles>'
) % W

content_types = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
    '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
    '<Default Extension="xml" ContentType="application/xml"/>'
    '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
    '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
    '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
    '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
    '</Types>'
)
rels = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
    '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
    '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>'
    '</Relationships>'
)
doc_rels = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
    '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
    '</Relationships>'
)
now = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
core = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
    'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" '
    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
    '<dc:title>\u5907\u8bfe\u6392\u8bfe\u7ba1\u7406\u7cfb\u7edf PRD</dc:title>'
    '<dc:creator>\u963f\u67a2</dc:creator>'
    '<cp:lastModifiedBy>\u963f\u67a2</cp:lastModifiedBy>'
    '<dcterms:created xsi:type="dcterms:W3CDTF">%s</dcterms:created>'
    '<dcterms:modified xsi:type="dcterms:W3CDTF">%s</dcterms:modified>'
    '</cp:coreProperties>' % (now, now)
)
app = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">'
    '<Application>WorkBuddy</Application><Company>\u72ec\u7acb\u8bed\u6587\u6559\u5e08</Company></Properties>'
)

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("[Content_Types].xml", content_types)
    z.writestr("_rels/.rels", rels)
    z.writestr("word/document.xml", document_xml)
    z.writestr("word/styles.xml", styles_xml)
    z.writestr("word/_rels/document.xml.rels", doc_rels)
    z.writestr("docProps/core.xml", core)
    z.writestr("docProps/app.xml", app)

print("WROTE", OUT, os.path.getsize(OUT), "bytes")
