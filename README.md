# Megrim Redis Microservice (Spring Boot)

Real-time order and terminal coordination service for Megrim. Uses Redis for
active order state and WebSockets for live updates between customers and
merchant terminals.

## What It Does

- Accepts order actions over WebSocket
- Validates and forwards order processing to the Postgres service
- Stores active order state in Redis
- Broadcasts updates to terminal sessions
- Sends push notifications (APNs)

## Tech Stack

- Java + Spring Boot
- Redis (Jedis)
- WebSockets

## Local Development

1. Ensure Redis is running locally.
2. Set `API_ENV` to match the Postgres environment.
3. Run with Maven:
   ```bash
   ./mvnw spring-boot:run
   ```

## Configuration

- `API_ENV`: `test` or `live`
- Redis host/port are in `src/main/resources/application.properties`

## Related Services

- Postgres microservice: order processing + business logic
- Flutter app: customer ordering + live updates
- NextJS admin: merchant dashboard
