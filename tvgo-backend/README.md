# tvGO Middleware Backend (Python / FastAPI)

This service implements the middleware API for **middleware.tvgo.cloud** that feeds the Android TV app at **app.tvgo.cloud**.

It follows the backend contract for:

- `/api/config`
- `/api/channels`
- `/api/channels/{id}`
- `/api/channels/{id}/epg`
- `/api/movies`
- `/api/movies/{id}`
- `/api/rails`

and also exposes admin endpoints used by the web middleware UI:

- `/api/auth/login` (username/password, JWT)
- `/api/auth/bootstrap-admin`
- `/api/admin/channels` CRUD
- `/api/admin/movies` CRUD
- `/api/admin/rails` CRUD
- `/api/admin/config` brand config
- `/api/admin/upload-image` (upload to AWS S3)
- `/api/admin/ingest/m3u` (ingest IPTV playlist and create channels)

## Quick start (local)

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

uvicorn app.main:app --reload
```

API will be available at: `http://localhost:8000`

Open interactive docs at: `http://localhost:8000/docs`

## Environment

Create `.env` in the project root:

```env
APP_NAME=tvGO
SECRET_KEY=CHANGE_ME_SUPER_SECRET

MONGO_URI=mongodb+srv://<user>:<password>@cluster.mongodb.net/?retryWrites=true&w=majority
MONGO_DB_NAME=tvGO

ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin

AWS_REGION=eu-central-1
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=yyy
S3_BUCKET_NAME=your-bucket-name
S3_PUBLIC_BASE_URL=https://cdn.example.com
```

(Or use the variable names from `app/config.py`.)

Then hit once:

```bash
curl -X POST http://localhost:8000/api/auth/bootstrap-admin
```

Now login from middleware UI using `admin` / `admin` and use the returned Bearer token.

### Seed demo data

Populate MongoDB Atlas with the demo channels, rails, branding, and movies required by the Android app:

```bash
python scripts/seed_data.py
```

This script loads the playlist provided above, creates rich metadata (logos, EPG, program schedules), and seeds the demo users (`demo_user` / `demo_pass` and the admin account).

## Deploy on server

Use the provided Dockerfile:

```bash
docker build -t tvgo-middleware .
docker run -d --name tvgo-middleware -p 8000:8000 --env-file .env tvgo-middleware
```

Point `middleware.tvgo.cloud` (Cloudflare) to this container (behind nginx / load balancer) and configure the Android TV app `app.tvgo.cloud` to call this API.

## Deploy to AWS Lambda (container image)

The project also ships with a Lambda-ready image definition (`Dockerfile.lambda`) that uses [Mangum](https://github.com/jordaneremieff/mangum) to adapt the FastAPI app to API Gateway/Lambda.

1. Build the Lambda container image locally:

   ```bash
   docker build -f Dockerfile.lambda -t tvgo-middleware-lambda .
   ```

2. Authenticate to Amazon ECR and create (or reuse) a repository, e.g. `tvgo-middleware-lambda`.

3. Tag and push the image:

   ```bash
   aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.<region>.amazonaws.com
   docker tag tvgo-middleware-lambda:latest <aws_account_id>.dkr.ecr.<region>.amazonaws.com/tvgo-middleware-lambda:latest
   docker push <aws_account_id>.dkr.ecr.<region>.amazonaws.com/tvgo-middleware-lambda:latest
   ```

4. Create a Lambda function using the container image and configure an HTTP API Gateway trigger. Set the environment variables from `.env` (Mongo URI, JWT secrets, etc.).

5. (Optional) Attach the same image to AWS Lambda Function URLs for direct HTTPS access.

The Lambda entrypoint is `app.main.handler`, exposed automatically by the container image.
