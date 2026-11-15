"""正文抽取单测。"""

from crawler_mcp.extract import extract_main


def test_extract_title_and_text():
    html = """
    <html><head><title>测试标题</title></head>
    <body><article><h1>测试标题</h1><p>这是一段足够长的正文内容用于抽取验证。</p>
    <p>第二段继续说明页面主题与要点。</p></article></body></html>
    """
    out = extract_main(html, url="https://example.com/a")
    assert "测试" in out["title"]
    assert len(out["text"]) > 10


def test_fallback_strip_tags():
    html = "<html><body><p>简单段落</p></body></html>"
    out = extract_main(html)
    assert "简单段落" in out["text"]
