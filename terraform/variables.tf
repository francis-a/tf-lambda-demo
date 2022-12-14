variable "stack_name" {
  default = "tf-lambda-demo"
}
variable "stack_environment" {
  default = "dev"
}

variable "deployable_jar" {
  default = "build/libs/tf-lambda-demo-0-SNAPSHOT.jar"
}