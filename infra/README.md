# Media V2 infrastructure

`cloudformation/media-v2.yaml` creates a private S3 bucket, S3 event to SQS
pipeline, DLQ, CloudFront OAC distribution, scoped API/Worker roles, and alarms.
It does not import existing resources or execute a legacy image backfill.

1. Copy `cloudformation/parameters.example.json` outside the repository and set
   the existing EC2 instance role ARN and exact browser origin. The existing
   role must also be authorized to call `sts:AssumeRole` for the two role ARNs
   emitted by this stack.
2. Run `scripts/validate-template.sh`.
3. Set `STACK_NAME`, `PARAMETERS_FILE`, and `CHANGE_SET_TYPE=CREATE` for the
   first deployment, then run `scripts/create-change-set.sh`.
4. Review and execute the Change Set through the approved operational process.
5. Inject stack outputs into `deployment/ec2-compose/.env` and enable Media V2
   only after the migration and V1 smoke test pass.

The production release runner reads those non-secret outputs from the
root-owned `/etc/community/media-v2.env` file (or `MEDIA_RUNTIME_ENV`). Copy
the `MEDIA_*` keys from `deployment/ec2-compose/compose.example.env`, replace
the placeholders with reviewed stack outputs, and set `MEDIA_V2_ENABLED=true`
before the Backend+Worker rollout.

The scripts create and inspect resources but intentionally never execute a
Change Set, import resources, backfill images, or deploy production.

The distribution currently uses the default `*.cloudfront.net` certificate.
CloudFront fixes that certificate's security policy to `TLSv1`, so the
template leaves `MinimumProtocolVersion` unset to match the AWS-managed value
and prevent false drift. Enforcing `TLSv1.2_2021` requires an alternate domain
name and an ACM certificate issued in `us-east-1`; configure those together
before adding the minimum protocol property.

The API role can issue a short-lived Presigned GET for `private/media/*` and
publish `MEDIA_REVISION` jobs to SQS. Browser access to that signed master is
limited by the bucket CORS rule to the configured `FrontendOrigin`; CloudFront
continues to expose only `public/media/*`. The Worker creates immutable
revision variants, while the post update transaction changes the active
revision pointer only after the requested revision is `READY`.

After publishing an immutable Backend image, set `WORKER_IMAGE` and run
`scripts/verify-worker-image.sh` to check the non-root user, read-only root,
ImageMagick coder/delegate policy, resource limits, and scratch write/cleanup.

`scripts/dry-run-legacy-backfill.sh` is a read-only inventory command. It
reports only aggregate legacy post/profile candidate counts and performs no
uploads, pointer changes, or database writes. An operationally approved
backfill can use that inventory as its preflight; this repository does not
automatically execute the migration.
