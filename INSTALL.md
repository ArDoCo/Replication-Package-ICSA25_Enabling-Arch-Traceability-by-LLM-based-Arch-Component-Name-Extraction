# Installation Instructions
This file guides you in setting up everything you need to run the Replication package.

Everything was tested on Linux (amd64) and MacOS (arm64). Windows should work as well, but we did not test it.

## Hardware / Service Requirements
* We recommend the execution on a system with at least 16 GB RAM.
* If you want to run the LLMs with **new** projects, you need ...
  * an [ollama](https://ollama.com/) instance capable of running LLAMA 3.1 70b.
  * an OpenAI access token and an organization id.
  * For the projects, we used in the paper, we provide the cached LLM responses. Thus, you don't need access to an ollama instance or an OpenAI access token.

## Prerequisites (Docker Image with all bundled dependencies)
* Docker

## Prerequisites (Local)
* Java JDK 21
* Maven 3
