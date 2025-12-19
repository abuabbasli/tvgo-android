from fastapi import HTTPException, status


def http_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status_code, detail={"error": {"code": code, "message": message}})


def unauthorized(message: str = "Unauthorized", code: str = "UNAUTHORIZED") -> HTTPException:
    return http_error(status.HTTP_401_UNAUTHORIZED, code, message)


def not_found(message: str, code: str = "NOT_FOUND") -> HTTPException:
    return http_error(status.HTTP_404_NOT_FOUND, code, message)


def bad_request(message: str, code: str = "BAD_REQUEST") -> HTTPException:
    return http_error(status.HTTP_400_BAD_REQUEST, code, message)


def forbidden(message: str, code: str = "FORBIDDEN") -> HTTPException:
    return http_error(status.HTTP_403_FORBIDDEN, code, message)
