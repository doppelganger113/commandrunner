# Commandrunner

![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/doppelganger113/commandrunner/test.yaml)

Server application in Spring boot for processing CSV files from an SFTP server and executing rows as commands
through HTTP against another system with failure, retrying and other functionality.

TODO: project still ongoing


Building the Docker container
```bash
docker build -t commandrunner:0.0.1 .
```

### Things to think about

- Adding support for SQLite for cases when we want this server to be simple and standalone. For cases when
someone wants it to do some simple big batches a little bit without big complexities of using PostgresSQL.

- Think if this is a good book: [The Art of PostgresSQL](https://theartofpostgresql.com)

- Someone elses job implementation [Neoq](https://github.com/acaloiaro/neoq)

### References

 - [Postgres queue tech](https://adriano.fyi/posts/2023-09-24-choose-postgres-queue-technology/)
 - [Practical SQL, 2nd Edition](https://nostarch.com/practical-sql-2nd-edition
 - [Indexing](https://use-the-index-luke.com)