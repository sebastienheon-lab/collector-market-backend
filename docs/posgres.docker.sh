docker run -d \
  --name my-postgres \
  -e POSTGRES_DB=mydatabase \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypassword \
  -e PGDATA=/var/lib/postgresql/data/pgdata \
  -p 5433:5432 \
  -v /path/on/host/pgdata:/var/lib/postgresql/data/pgdata \
  postgres:latest