resource "aws_dynamodb_table" "messages_dynamo_table" {
  name         = "${local.name}-messages"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "message_id"

  attribute {
    name = "message_id"
    type = "S"
  }

  stream_enabled = true
  #  Create a stream that contains both the previous and current record
  stream_view_type = "NEW_AND_OLD_IMAGES"
}
