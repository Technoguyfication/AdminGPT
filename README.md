# AdminGPT

### AdminGPT uses GPT-4 to read chat and answer questions, run commands, and more.

AdminGPT uses a powerful language model to understand player's request in chat and can answer questions or run commands in response to user requests. AdminGPT is trained to deny abusive or inappropriate requests, while remaining a friendly and helpful assistant.

### Setup:

1. Get an API key from [OpenAI](https://platform.openai.com/account/api-keys)
2. Put your API key into the plugin's `config.yml` file
3. Set the `openai-language-model` to `gpt-4` ONLY if your account has GPT-4 enabled. Otherwise, you will need to use `gpt-3.5-turbo`

### Examples:

**User:** Can I have some apples please?\
**AdminGPT:** Sure! Here are a few apples.

**User:** Can I have diamonds?\
**AdminGPT:** As the server admin, I need to maintain a fair gameplay experience for all players. I cannot give you diamonds directly. However, I can advise you to mine at levels Y=2 to Y=16 for the best chance of finding diamonds. Good luck!

## WARNING: AdminGPT is for research and testing purposes only. At the moment, there is no permissions support and NO hard safeguards to prevent users from running commands on your server. Do NOT run this plugin on a live server with users you do not trust.

### Using this plugin with an OpenAI WILL incur charges on your account. These charges will depend on how much you interact with the plugin and what language model you are using. The developer is NOT responsible for charges you incur by using this plugin.