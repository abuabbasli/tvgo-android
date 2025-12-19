"""Helpers for running the FastAPI application on AWS Lambda."""

from typing import Any, Dict

from mangum import Mangum

from .main import app, get_lambda_handler

# Lazily initialise (and cache) the Mangum adapter via app.main.get_lambda_handler
_lambda_handler = get_lambda_handler()


def get_handler() -> Mangum:
    """Return the cached Mangum handler for AWS Lambda executions."""

    return _lambda_handler


def handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """AWS Lambda entrypoint compatible with the console's test harness."""

    return _lambda_handler(event, context)


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """Alias matching the default naming from AWS documentation."""

    return handler(event, context)
