FROM maven:3-eclipse-temurin-21
WORKDIR /replication
COPY . .
# Download all sources and go offline
RUN mvn -B compile test-compile && mvn -B dependency:go-offline

ENV OPENAI_API_KEY=sk-DUMMY
ENV OPENAI_ORG_ID=""
ENV OLLAMA_HOST=http://localhost:11434

ENTRYPOINT bash -c "cat README.md && bash"
