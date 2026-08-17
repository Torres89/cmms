FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# No port: this service only makes outbound calls, to machines on the shop
# network and to the API.
CMD ["python", "main.py"]
