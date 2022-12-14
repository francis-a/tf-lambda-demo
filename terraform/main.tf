terraform {
  backend "s3" {
    key     = "tf.state"
    region  = "us-east-1"
    encrypt = false
  }
}

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.33.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
  #  AWS profile to run this under
  profile = var.aws_profile
  #  Tags are applied to all resources in the stack
  #  These tags are used
  default_tags {
    tags = {
      Service     = var.stack_name
      Environment = var.stack_environment
    }
  }
}

locals {
  name = "${var.stack_name}-${var.stack_environment}"
}

## Resource group for organization
resource "aws_resourcegroups_group" "lambda_demo" {
  name = local.name
  resource_query {
    query = jsonencode({
      ResourceTypeFilters = ["AWS::AllSupported"]
      TagFilters = [
        {
          Key    = "Service"
          Values = [var.stack_name]
        },
        {
          Key    = "Environment"
          Values = [var.stack_environment]
        }
      ]
    })
  }
}

