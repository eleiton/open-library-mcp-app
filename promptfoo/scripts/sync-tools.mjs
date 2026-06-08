// Fetches tool schemas from the running MCP server and writes them to
// tools/search-books.json in OpenAI tools format. Run this after editing
// @McpTool / @McpToolParam descriptions in BookSearchApp.java so the
// promptfoo configs see the same schema the real MCP integration sees.
//
// Usage:
//   1. Start the MCP server:   ./scripts/dev.sh   (or ./gradlew bootRun)
//   2. Install deps once:      cd promptfoo && npm install
//   3. Sync:                   cd promptfoo && npm run sync-tools
//
// Override the server URL with MCP_URL if you're testing a non-default port
// or a remote tunnel:
//   MCP_URL=https://your-tunnel.trycloudflare.com/mcp npm run sync-tools

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const MCP_URL = process.env.MCP_URL || 'http://localhost:3001/mcp';
const here = dirname(fileURLToPath(import.meta.url));
const OUTPUT = join(here, '..', 'tools', 'search-books.json');

const transport = new StreamableHTTPClientTransport(new URL(MCP_URL));
const client = new Client({ name: 'promptfoo-tool-sync', version: '1.0.0' });

try {
  await client.connect(transport);
  const { tools } = await client.listTools();

  // MCP tool shape -> OpenAI function-calling shape.
  const openaiTools = tools.map((t) => ({
    type: 'function',
    function: {
      name: t.name,
      description: t.description,
      parameters: t.inputSchema,
    },
  }));

  writeFileSync(OUTPUT, JSON.stringify(openaiTools, null, 2) + '\n');
  console.log(`Wrote ${openaiTools.length} tool(s) to ${OUTPUT}`);
  for (const t of openaiTools) {
    console.log(`  - ${t.function.name}`);
  }
} finally {
  await client.close().catch(() => {});
}
