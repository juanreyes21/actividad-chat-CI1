#!/bin/bash
echo "=== Starting all services with Supervisor ==="
supervisord -c /app/supervisord.conf
