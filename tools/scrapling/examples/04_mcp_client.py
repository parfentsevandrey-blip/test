import asyncio, os
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
V = os.environ.get("SCRAPLING_BIN", "scrapling")
EXE = os.environ.get("SCRAPLING_CHROMIUM")
async def main():
    params = StdioServerParameters(command=V, args=["mcp"] + (["--executable-path", EXE] if EXE else []))
    async with stdio_client(params) as (r, w):
        async with ClientSession(r, w) as s:
            await s.initialize()
            tools = (await s.list_tools()).tools
            print(len(tools), "tools:", ", ".join(t.name for t in tools))
            res = await s.call_tool("make_request", {"url": "https://quotes.toscrape.com/", "css_selector": ".author"})
            print("make_request ->", str(res.content[0].text)[:200].replace("\n", " "))
asyncio.run(main())
