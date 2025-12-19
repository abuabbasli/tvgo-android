"""AWS Lambda entrypoints for the tvGO FastAPI service.

This module mirrors the default structure AWS expects when you deploy the
application as a ZIP archive or when you override the handler name while using
container images.  Both ``handler`` and ``lambda_handler`` are exposed so you
can reference either value from the Lambda console without importing from the
``app`` package directly.
"""

from app.lambda_helper import handler, lambda_handler  # noqa: F401

__all__ = ["handler", "lambda_handler"]
