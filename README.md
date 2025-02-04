# MCP Strava Server

## Overview
The **MCP Strava Server** facilitates seamless integration between Strava APIs and Claude for Desktop. By using this server, you can process and interact with Strava activity data efficiently through Claude's MCP framework.

---

## How to Set Up

### 1. Clone the Repository
Clone the MCP Strava Server repository to your local machine:
```bash
git clone https://github.com/MariyaFilippova/mcp-strava.git
```

### 2. Configure Your CLIENT_ID and CLIENT_SECRET
To set up your Strava API credentials, change  `src/main/resources/.env` file. Add your `CLIENT_ID` and `CLIENT_SECRET` obtained from [Strava API settings](https://www.strava.com/settings/api) into the file as shown below:

```dotenv
CLIENT_ID="your-client-id"
CLIENT_SECRET="your-client-secret"
```

   
### 3. Build the Project
Use Gradle to build the project and generate the executable JAR file:
```bash
gradle shadowJar
```

The built server will be available at: `build/libs/strava-mcp-server-1.0.0-all.jar`


### 4. Configure Claude for Desktop

To connect the MCP Strava Server to Claude for Desktop:

#### a. Install Claude for Desktop
Ensure that Claude for Desktop is installed on your machine. If you don’t already have it, [download the latest version here](https://claude.ai/download).

#### b. Update Claude’s Configuration
Modify the configuration file for Claude to register the MCP Strava Server:
1. Open the Claude configuration file:
    ```bash
    code ~/Library/Application\ Support/Claude/claude_desktop_config.json
    ```
2. Add the MCP Strava Server configuration:
    ```json
    {
      "mcpServers": {
        "strava": {
          "command": "java",
          "args": [
            "-jar",
            "your/path/strava-mcp-server/build/libs/strava-mcp-server-1.0.0-all.jar"
          ]
        }
      }
    }
    ```
   Replace `your/path` with the absolute path where the JAR file is located.

3. Save your changes.

#### c. Restart Claude for Desktop
Restart the Claude for Desktop application to apply the updated configuration.

---

## Congratulations!
The MCP Strava Server is now set up and configured. You are ready to use it with Claude for Desktop to interact with Strava activity data seamlessly.

Enjoy managing your Strava activities with ease! 🚴‍♀️ 🚴‍♂️
