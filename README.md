# TwitchOverlayServer

An HTTP and WebSocket server coded in Java to provide and serve Twitch Overlays to use in OBS/XSplit

[Live Server](https://overlay.logicism.tv/)

## Twitch Overlays
- Predictions
- Polls
- Alert Box
  - Follows
  - Subscriptions
  - Cheers
  - Raids
- 
- More to Come...

## Features
- HTTP Server (to be used in conjuction with a reverse proxy for HTTP & HTTPS)
  - Static HTML Resources Server
  - HTML Resources Server
- WebSocket Server
  - Automated Interval Ping/Pong
  - Automatic Reconnection of Server to Client
- Webhook Receiving
  - Twitch Webhook Challenge Verification

## How to Self-Host

Download the [latest release](https://github.com/LogicismDev/TwitchOverlayServer/releases), edit the configuration (config.yml), and use the provided .bat or .sh file to run the server.

Please note that this requires the usage of Java 8 or higher to use the server.

## Modifying HTML Files

You are more than welcome to add your own html files or modify the current ones in the pages directory as specified.

The only mandatory files required in the pages directory are the following below:
- index.html
- callback.html
- callbackError.html
- 404.html
- privacy.html
- terms.html
- overlay/predictions.html
- overlay/alert.mp3
- overlay/alerts.html
- overlay/polls.html