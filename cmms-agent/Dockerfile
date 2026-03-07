FROM python:3.11-slim

WORKDIR /app

# Install dependencies first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Create browser data directory so Playwright doesn't error out even if unused
RUN mkdir -p .browser-data

# Copy the rest of the application
COPY . .

# Expose the API port
EXPOSE 8001

# Run the FastAPI server
CMD ["python", "server.py"]
