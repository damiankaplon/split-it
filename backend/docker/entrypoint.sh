#!/bin/sh
# Refuses to start without an OpenAI API key, so a missing secret shows up at deploy time
# instead of as failing POST /recognition requests later.
# The key is read by Spring from either source (see spring.config.import in application.properties):
#   - Docker secret file /run/secrets/OPENAI_API_KEY (preferred: not visible in `docker inspect`)
#   - OPENAI_API_KEY environment variable
secret_file=/run/secrets/OPENAI_API_KEY

if [ -s "$secret_file" ] || [ -n "$OPENAI_API_KEY" ]; then
  exec java -jar /app.jar "$@"
fi

echo "ERROR: OpenAI API key is missing. Provide the Docker secret OPENAI_API_KEY (mounted at $secret_file) or the OPENAI_API_KEY environment variable." >&2
exit 1
