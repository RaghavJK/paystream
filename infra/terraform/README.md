# Terraform (roadmap step 13)

Empty on purpose. When you reach step 13, create:

- `versions.tf`: Terraform and AWS provider versions, remote state in S3 with locking.
- `network.tf`: VPC, two private subnets, two public subnets.
- `msk.tf` or `sqs.tf`: the managed messaging you choose, and why (write an ADR).
- `rds.tf`: PostgreSQL, Multi-AZ off for cost, deletion protection off for a sandbox.
- `eks.tf` or `ecs.tf`: where the three services run.
- `iam.tf`: one role per service, least privilege; the agent role may call `bedrock:InvokeModel` only.

Run `terraform plan` before every `apply`, and `terraform destroy` when you finish a session.
