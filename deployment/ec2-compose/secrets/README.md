# Runtime secrets

Real secret values never belong in this repository or in Compose environment
files. On EC2, `prepare-directories.sh` creates these empty files:

- `/etc/community/secrets/mysql-root-password`
- `/etc/community/secrets/mysql-app-password`
- `/etc/community/secrets/jwt-secret`

The files are owned by `root:community-secrets` with mode `0640`. Only the
containers explicitly assigned supplemental group ID `20000` can read their
mounted secret. The backend and MySQL receive the application database password
from the same source file; the JWT secret is mounted only into the backend.

Enter values interactively on EC2. Do not put them in shell history or command
arguments:

```bash
sudoedit /etc/community/secrets/mysql-root-password
sudoedit /etc/community/secrets/mysql-app-password
sudoedit /etc/community/secrets/jwt-secret
```

Each file must contain exactly one non-empty line with no surrounding quotes.
Use an independently generated database password. The JWT value must be a
base64-encoded random value representing at least 32 random bytes.

Validate metadata without printing contents:

```bash
sudo stat -c '%U:%G %a %n' /etc/community/secrets/*
sudo test -s /etc/community/secrets/mysql-root-password
sudo test -s /etc/community/secrets/mysql-app-password
sudo test -s /etc/community/secrets/jwt-secret
```

Expected ownership and mode are `root:community-secrets 640`.
