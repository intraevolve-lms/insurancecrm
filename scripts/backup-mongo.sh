#!/usr/bin/env bash
# Scheduled MongoDB Atlas backup — run via cron on the deployment VM.
#
# MongoDB Atlas's free (M0) tier replicates your data across 3 nodes for
# high availability, but that protects against infrastructure failure only —
# it does NOT protect against logical mistakes (a bad bulk-update, a buggy
# migration, accidental deletion), since those get replicated everywhere too.
# Point-in-time backup/restore is an Atlas M10+ paid feature, so on the free
# tier this script is what stands in for it.
#
# Usage:
#   MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/insuredindex" \
#   BACKUP_DIR="/opt/insuredindex/backups" \
#   RETENTION_DAYS=14 \
#   ./backup-mongo.sh
#
# Example crontab entry (daily at 2am):
#   0 2 * * * MONGODB_URI="..." BACKUP_DIR="/opt/insuredindex/backups" /opt/insuredindex/scripts/backup-mongo.sh >> /var/log/insuredindex-backup.log 2>&1

set -euo pipefail

: "${MONGODB_URI:?MONGODB_URI must be set}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ARCHIVE="$BACKUP_DIR/insuredindex-$TIMESTAMP.gz"

echo "[$(date)] Starting backup to $ARCHIVE"

# Uses the official MongoDB database tools image — no local mongodump install required.
docker run --rm \
  -v "$BACKUP_DIR:/backup" \
  mongodb/mongodb-database-tools:latest \
  mongodump --uri="$MONGODB_URI" --gzip --archive=/backup/"$(basename "$ARCHIVE")"

echo "[$(date)] Backup complete: $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"

# Prune backups older than RETENTION_DAYS
find "$BACKUP_DIR" -name 'insuredindex-*.gz' -mtime "+$RETENTION_DAYS" -print -delete

echo "[$(date)] Pruned backups older than $RETENTION_DAYS days"

# Optional: copy $ARCHIVE off this VM to durable storage (e.g. OCI Object Storage via `oci os object put`,
# or `rclone copy`) so a lost/corrupted VM doesn't also take the backups with it. Left unconfigured here —
# add your preferred upload command once the target storage is set up.
