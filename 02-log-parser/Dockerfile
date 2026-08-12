FROM python:3.12-alpine
WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code and sample data
COPY log_parser/ log_parser/
COPY sample_logs/ sample_logs/

# Create output directory
RUN mkdir -p /app/output

# Default entrypoint: run the CLI tool
# Override args with e.g. docker compose run log-parser -i sample_logs/server.log --excel
ENTRYPOINT ["python", "-m", "log_parser.cli"]
CMD ["-i", "sample_logs/access.csv", "-o", "/app/output", "--excel", "--sql", "--csv-output"]
