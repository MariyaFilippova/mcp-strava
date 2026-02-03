# MCP Strava Server

A Model Context Protocol (MCP) server that integrates Strava with Claude for Desktop, enabling AI-powered analysis of your fitness activities.

## Features

- **OAuth Authentication** with automatic token refresh and persistence
- **Activity Analysis** - get details on recent activities
- **Historical Comparisons** - compare months year-over-year
- **Statistics** - all-time stats, weekly/monthly summaries
- **Filtering** - by activity type (Run, Ride, Swim, etc.)

## Available Tools

| Tool | Description |
|------|-------------|
| `auth_strava` | Authorize with Strava (opens browser) |
| `logout` | Clear stored tokens to switch accounts |
| `last_activity` | Get details of your most recent activity |
| `recent_activities` | List last 10 activities |
| `athlete_stats` | All-time statistics (rides, runs, swims) |
| `activities_by_type` | Filter activities by sport type |
| `weekly_summary` | Summary of the past 7 days |
| `monthly_summary` | Summary of the past 30 days |
| `month_summary` | Summary for a specific month/year |
| `compare_months` | Compare two months (e.g., Jan 2025 vs Jan 2026) |
| `get_streams` | Heart rate data for last activity |

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/MariyaFilippova/mcp-strava.git
cd mcp-strava
```

### 2. Configure Strava API Credentials

Get your credentials from [Strava API settings](https://www.strava.com/settings/api).

Create/edit `src/main/resources/.env`:

```dotenv
CLIENT_ID="your-client-id"
CLIENT_SECRET="your-client-secret"
```

### 3. Build the Project

```bash
./gradlew shadowJar
```

The JAR will be at: `build/libs/strava-mcp-server-2.0.0-all.jar`

### 4. Configure Claude for Desktop

Edit Claude's configuration file:

```bash
# macOS
code ~/Library/Application\ Support/Claude/claude_desktop_config.json

# Windows
code %APPDATA%\Claude\claude_desktop_config.json
```

Add the MCP server:

```json
{
  "mcpServers": {
    "strava": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-strava/build/libs/strava-mcp-server-2.0.0-all.jar"
      ]
    }
  }
}
```

Restart Claude for Desktop.

## Usage Examples

Once configured, you can ask Claude things like:

- "Show me my recent Strava activities"
- "What are my all-time running stats?"
- "Compare my January 2025 with January 2026"
- "Give me a summary of my cycling this week"
- "How did my training this month compare to last month?"

## Authentication

On first use, the server will open your browser for Strava authorization. Tokens are persisted to `~/.strava-mcp-token.json` and automatically refreshed when expired.

Use the `logout` tool to clear stored tokens if you need to switch accounts.

## License

MIT