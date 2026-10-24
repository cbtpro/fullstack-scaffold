#!/usr/bin/env bash
# Docker 生命周期入口：由 Maven 的 docker profile 调用，也可在仓库根直接执行。
# 只依赖 bash + docker compose，不需要 Python 或 jq。
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
COMPOSE_FILE="$REPO/docker/docker-compose.yml"
# 未指定 -Ddocker.data.dir 且环境文件也没写 FS_DATA_PATH 时的兜底数据根
DEFAULT_DATA_DIR="/Volumes/WD_BLACK/docker/docker_files"
PLATFORM="unix"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) PLATFORM="window" ;;
esac

APPLICATIONS=(gateway-service user-service web-react web-vue)
ACTIONS="status config init migrate build create up start stop restart delete deploy remove-image"

# 数据目录名、迁移标记键、历史命名卷名三者的下标必须一一对应
DATA_DIRS=(fs_mysql_data fs_redis_data fs_nacos_data fs_nacos_logs)
MIGRATION_KEYS=(mysql redis nacos-data nacos-logs)
VOLUME_LEGACY=(fullstack-scaffold_fs-mysql-data fullstack-scaffold_fs-redis-data
               fullstack-scaffold_fs-nacos-data fullstack-scaffold_fs-nacos-logs)

MODULE=""
ROOT_MODULE=""
ACTION="status"
DATA_DIR=""
WAIT_TIMEOUT="240"
SELECTED=""

LOCK_DIR=""
LOCK_HELD=0
BUILD_TMP=""
MYSQL_SOURCE=""

die() {
    printf 'Docker 生命周期失败：%s\n' "$*" >&2
    exit 1
}

cleanup() {
    if [ -n "$MYSQL_SOURCE" ]; then
        docker stop "$MYSQL_SOURCE" >/dev/null 2>&1 || true
        docker rm "$MYSQL_SOURCE" >/dev/null 2>&1 || true
    fi
    if [ -n "$BUILD_TMP" ] && [ -d "$BUILD_TMP" ]; then
        rm -rf "$BUILD_TMP"
    fi
    if [ "$LOCK_HELD" = "1" ]; then
        rm -f "$LOCK_DIR/pid"
        rmdir "$LOCK_DIR" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# 平台专属环境文件优先（.env.unix / .env.window），再退回通用 .env。
resolve_env_file() {
    local name
    for name in ".env.$PLATFORM" ".env"; do
        if [ -f "$REPO/docker/$name" ]; then
            printf '%s\n' "$REPO/docker/$name"
            return 0
        fi
    done
    return 1
}

# 只读取单个键，不 source 环境文件，避免执行里面的任意内容。
env_value() {
    local file="$1" key="$2" value
    value="$(awk -v key="$key" '
        /^[[:space:]]*#/ { next }
        {
            line = $0
            sub(/\r$/, "", line)
            if (line ~ "^[[:space:]]*" key "[[:space:]]*=") {
                sub("^[[:space:]]*" key "[[:space:]]*=[[:space:]]*", "", line)
                print line
            }
        }
    ' "$file" | tail -1)"
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
    printf '%s\n' "$value"
}

compose() {
    local cmd=(docker compose -p fullstack-scaffold -f "$COMPOSE_FILE") env
    env="$(resolve_env_file)" || env=""
    [ -n "$env" ] && cmd+=(--env-file "$env")
    # 数据目录只由 -Ddocker.data.dir 与环境文件决定，命令行解析出的值统一覆盖进去
    FS_DATA_PATH="$DATA_DIR" "${cmd[@]}" "$@"
}

preflight() {
    docker info >/dev/null 2>&1 || die "无法连接 Docker 守护进程"
    docker compose version >/dev/null 2>&1 || die "未安装 Docker Compose v2"
}

# 只认「卷不存在」这一种情况；其它报错（权限、守护进程异常）必须向上暴露，
# 否则会被误判成缺失卷而悄悄新建空卷。
volume_exists() {
    local name="$1" message
    if message="$(docker volume inspect "$name" 2>&1 >/dev/null)"; then
        return 0
    fi
    case "$message" in
        *"no such volume"*|*"No such volume"*) return 1 ;;
        *) die "$message" ;;
    esac
}

# bind 的 device 指向 Docker 守护进程所在机器的文件系统，远程守护进程下没有意义。
local_storage() {
    local context endpoint
    context="$(docker context show)"
    endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$context")}"
    case "$endpoint" in
        unix://*) ;;
        *) die "持久化操作请在 Docker 服务器上执行 Maven（仅支持本机 unix socket）" ;;
    esac
}

acquire_lock() {
    if [ ! -d "$DATA_DIR" ]; then
        mkdir -p "$DATA_DIR"
        chmod 700 "$DATA_DIR"
    fi
    LOCK_DIR="$DATA_DIR/.lifecycle.lock.d"
    if mkdir "$LOCK_DIR" 2>/dev/null; then
        LOCK_HELD=1
        echo $$ > "$LOCK_DIR/pid"
        return
    fi
    local owner=""
    [ -f "$LOCK_DIR/pid" ] && owner="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
    if [ -n "$owner" ] && kill -0 "$owner" 2>/dev/null; then
        die "另一个 Docker 生命周期操作正在进行（pid ${owner}）"
    fi
    # 上一位进程已不存在，清理失效锁后重试一次
    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR" 2>/dev/null || die "无法获取生命周期锁：$LOCK_DIR"
    LOCK_HELD=1
    echo $$ > "$LOCK_DIR/pid"
}

# 数据目录由 compose 直连 bind 到宿主，init 只负责建目录与旧卷闸门。
prepare_storage() {
    local migrating="$1"
    local i directory legacy
    local_storage
    if [ "$migrating" = "0" ]; then
        for i in "${!DATA_DIRS[@]}"; do
            directory="${MIGRATION_KEYS[$i]}"
            legacy="${VOLUME_LEGACY[$i]}"
            if volume_exists "$legacy" && [ ! -e "$DATA_DIR/.migrated-$directory" ]; then
                die "检测到旧数据卷；先执行 -Ddocker.action=migrate，避免启动空数据库"
            fi
        done
    fi
    for i in "${!DATA_DIRS[@]}"; do
        mkdir -p "$DATA_DIR/${DATA_DIRS[$i]}"
    done
}

# 从 compose 规范化后的配置里取服务镜像名，避免把镜像命名规则抄第二份
service_image() {
    local service="$1" image
    image="$(compose config 2>/dev/null | awk -v target="$service" '
        /^services:[[:space:]]*$/ { in_services = 1; next }
        in_services && /^[^[:space:]]/ { in_services = 0 }
        in_services && $0 == "  " target ":" { in_service = 1; next }
        in_service && /^  [^[:space:]]/ { in_service = 0 }
        in_service && /^    image:/ {
            sub(/^    image:[[:space:]]*/, "")
            print
            exit
        }
    ')"
    [ -n "$image" ] || die "无法从 compose 配置解析服务 $service 的镜像名"
    image="${image%\"}"; image="${image#\"}"
    image="${image%\'}"; image="${image#\'}"
    printf '%s\n' "$image"
}

# 只把源码复制到干净的临时目录再构建：外置卷（exFAT）会生成 ._* AppleDouble 文件，
# 这些文件会让 BuildKit 在处理 xattr 时直接失败。
build_images() {
    local service folders=(backend frontend docker)
    local excludes=(--exclude=node_modules --exclude=target --exclude=dist --exclude=.git
                    --exclude=.turbo --exclude=__pycache__ --exclude=.nitro
                    --exclude=.output --exclude=.cache --exclude=.DS_Store
                    --exclude='._*' --exclude='.env*' --exclude=docker/tests)
    BUILD_TMP="$(mktemp -d "${TMPDIR:-/tmp}/fs-docker-build-XXXXXX")"
    for service in "${folders[@]}"; do
        [ -d "$REPO/$service" ] || continue
        mkdir -p "$BUILD_TMP/$service"
        tar -C "$REPO" "${excludes[@]}" -cf - "$service" | tar -C "$BUILD_TMP" -xf -
    done
    cp "$REPO/.dockerignore" "$BUILD_TMP/.dockerignore"
    local targets="$SELECTED"
    [ -n "$targets" ] || targets="${APPLICATIONS[*]}"
    for service in $targets; do
        docker build --file "$BUILD_TMP/docker/$service/Dockerfile" \
            --tag "$(service_image "$service")" "$BUILD_TMP"
    done
    rm -rf "$BUILD_TMP"
    BUILD_TMP=""
}

up() {
    local args=(up -d --no-build --wait --wait-timeout "$WAIT_TIMEOUT")
    [ -n "$SELECTED" ] && args+=(--no-deps)
    compose "${args[@]}" $SELECTED
}

migrate_mysql() {
    local legacy="$1"
    local backup="$DATA_DIR/backups"
    local dump="$backup/mysql-migration.sql"
    local pending="$DATA_DIR/.mysql-import-pending"
    local partial="$backup/mysql-migration.sql.partial"
    local source="fullstack-scaffold-mysql-migration-source"
    local i ready=0
    mkdir -p "$backup"
    chmod 700 "$backup"
    if [ ! -f "$pending" ]; then
        # 上次失败的导出实例可以直接清掉；原始数据卷始终保留
        docker rm -f "$source" >/dev/null 2>&1 || true
        docker run -d --name "$source" --network none \
            -v "$legacy:/var/lib/mysql" mysql:8.0 --skip-networking >/dev/null
        MYSQL_SOURCE="$source"
        for i in $(seq 1 120); do
            if docker exec "$source" mysqladmin ping -uroot -proot123 >/dev/null 2>&1; then
                ready=1
                break
            fi
            sleep 1
        done
        [ "$ready" = "1" ] || die "旧 MySQL 导出实例启动超时；原数据卷保留"
        # 逻辑备份：避开 MySQL 在 macOS bind 挂载上的 lower_case_table_names 兼容问题
        docker exec "$source" mysqldump -uroot -proot123 --single-transaction \
            --routines --events --triggers --set-gtid-purged=OFF --databases fs_user > "$partial" \
            || die "旧 MySQL 导出失败；原数据卷保留"
        chmod 600 "$partial"
        mv "$partial" "$dump"
        printf '%s\n' "$legacy" > "$pending"
        docker stop "$source" >/dev/null 2>&1 || true
        docker rm "$source" >/dev/null 2>&1 || true
        MYSQL_SOURCE=""
    fi
    [ -s "$dump" ] || die "MySQL 迁移备份不存在或为空，拒绝导入"
    compose up -d --no-build --wait --wait-timeout "$WAIT_TIMEOUT" mysql
    compose exec -T mysql mysql -uroot -proot123 < "$dump"
    printf '%s\n' "$legacy" > "$DATA_DIR/.migrated-mysql"
    rm -f "$pending"
    echo "MySQL 逻辑迁移完成，备份保留于 $dump"
}

migrate() {
    [ -z "$SELECTED" ] || die "数据迁移必须由父 POM 统一执行"
    local_storage
    local i key directory legacy destination staging running
    local pending_indexes=()
    for i in "${!DATA_DIRS[@]}"; do
        key="${MIGRATION_KEYS[$i]}"
        legacy="${VOLUME_LEGACY[$i]}"
        if volume_exists "$legacy" && [ ! -e "$DATA_DIR/.migrated-$key" ]; then
            pending_indexes+=("$i")
        fi
    done
    if [ "${#pending_indexes[@]}" = "0" ]; then
        prepare_storage 0
        echo "数据卷已就绪，无需重复迁移。"
        return
    fi
    for i in "${pending_indexes[@]}"; do
        key="${MIGRATION_KEYS[$i]}"
        destination="$DATA_DIR/${DATA_DIRS[$i]}"
        if [ "$key" = "mysql" ] && [ -e "$DATA_DIR/.mysql-import-pending" ]; then
            continue
        fi
        if [ -d "$destination" ] && [ -n "$(ls -A "$destination" 2>/dev/null)" ]; then
            die "$destination 非空且没有迁移完成标记；拒绝覆盖"
        fi
        if [ -e "$DATA_DIR/.staging-$key" ]; then
            die "$destination 有未完成迁移，请检查 staging 后恢复；原卷仍保留"
        fi
    done
    running="$(compose ps --status running --services || true)"
    if printf '%s\n' "$running" | grep -qx redis; then
        compose exec -T redis redis-cli SAVE
    fi
    compose stop
    prepare_storage 1
    for i in "${pending_indexes[@]}"; do
        key="${MIGRATION_KEYS[$i]}"
        directory="${DATA_DIRS[$i]}"
        legacy="${VOLUME_LEGACY[$i]}"
        if [ "$key" = "mysql" ]; then
            migrate_mysql "$legacy"
            continue
        fi
        staging="$DATA_DIR/.staging-$key"
        mkdir "$staging"
        docker run --rm --network none \
            -v "$legacy:/source:ro" -v "$staging:/destination" \
            nginx:alpine sh -ec 'cp -a /source/. /destination/; sync'
        destination="$DATA_DIR/$directory"
        rmdir "$destination"   # 已确认是空目录，绝不删除用户数据
        mv "$staging" "$destination"
        printf '%s\n' "$legacy" > "$DATA_DIR/.migrated-$key"
        echo "迁移完成：$legacy -> ${destination}（原卷保留）"
    done
    if [ -n "$running" ]; then
        compose up -d --no-build --wait --wait-timeout "$WAIT_TIMEOUT" $running
    fi
}

execute() {
    local action="$1"
    preflight
    case "$action" in
        status)
            compose ps -a $SELECTED
            return
            ;;
        config)
            compose config --quiet
            return
            ;;
    esac
    acquire_lock
    case "$action" in
        migrate) migrate ;;
        init) prepare_storage 0 ;;
        build) build_images ;;
        up|start|create|deploy|restart)
            prepare_storage 0
            case "$action" in
                deploy) build_images ;;
                restart) compose stop $SELECTED ;;
            esac
            if [ "$action" = "create" ]; then
                compose up --no-start --no-build --no-deps $SELECTED
            else
                up
            fi
            ;;
        stop)
            compose stop $SELECTED
            ;;
        delete)
            if [ -n "$SELECTED" ]; then
                compose rm --stop --force $SELECTED
            else
                # 永不使用 --volumes / --rmi，数据卷与镜像必须保留
                compose down --remove-orphans
            fi
            ;;
        remove-image)
            local service image
            local targets="$SELECTED"
            [ -n "$targets" ] || targets="${APPLICATIONS[*]}"
            for service in $targets; do
                image="$(service_image "$service")"
                if [ -n "$(docker image ls --quiet "$image")" ]; then
                    docker image rm "$image"   # 仍在使用时由 Docker 拒绝，不强删
                fi
            done
            ;;
    esac
}

usage() {
    cat <<EOF
用法：$(basename "$0") --module <模块> --root-module <模块> [--data-dir <目录>] [--action <动作>]

数据目录优先级：--data-dir > 环境文件 FS_DATA_PATH > 默认 $DEFAULT_DATA_DIR
环境文件按平台选择：docker/.env.$PLATFORM，其次 docker/.env（模板见 docker/env.template）

动作：$ACTIONS
EOF
}

main() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --module) MODULE="${2:-}"; shift 2 ;;
            --root-module) ROOT_MODULE="${2:-}"; shift 2 ;;
            --action) ACTION="${2:-}"; shift 2 ;;
            --data-dir) DATA_DIR="${2:-}"; shift 2 ;;
            --wait-timeout) WAIT_TIMEOUT="${2:-}"; shift 2 ;;
            -h|--help) usage; exit 0 ;;
            *) die "未知参数：$1" ;;
        esac
    done
    [ -n "$MODULE" ] || die "缺少 --module"
    [ -n "$ROOT_MODULE" ] || die "缺少 --root-module"

    # 父 reactor 里每个子模块都会被调用一次，只让根模块执行
    [ "$MODULE" = "$ROOT_MODULE" ] || return 0

    # 数据目录优先级：-Ddocker.data.dir > 环境文件 FS_DATA_PATH > 平台默认
    if [ -z "$DATA_DIR" ]; then
        if env_file="$(resolve_env_file)"; then
            DATA_DIR="$(env_value "$env_file" FS_DATA_PATH)"
        fi
    fi
    if [ -z "$DATA_DIR" ]; then
        [ "$PLATFORM" = "unix" ] \
            || die "缺少数据目录：复制 docker/env.template 为 docker/.env.window 并设置 FS_DATA_PATH"
        DATA_DIR="$DEFAULT_DATA_DIR"
    fi

    case "$WAIT_TIMEOUT" in
        ''|*[!0-9]*) die "--wait-timeout 必须是正整数" ;;
    esac
    [ "$WAIT_TIMEOUT" -gt 0 ] || die "--wait-timeout 必须是正整数"

    case "$ACTION" in
        status|config|init|migrate|build|create|up|start|stop|restart|delete|deploy|remove-image) ;;
        *) die "不支持的动作：${ACTION}（可用：${ACTIONS}）" ;;
    esac

    case "$MODULE" in
        fullstack-scaffold-backend) SELECTED="" ;;
        gateway-service|user-service|web-react|web-vue) SELECTED="$MODULE" ;;
        *) die "$MODULE 是共享库，无独立 Docker 服务" ;;
    esac

    if [ "${DATA_DIR:0:1}" = "~" ]; then
        DATA_DIR="$HOME${DATA_DIR:1}"
    fi
    # 兼容 Windows 盘符路径（D:/…、D:\…），其余必须是绝对目录
    case "$DATA_DIR" in
        /*|[A-Za-z]:[\\/]*) ;;
        *) die "数据目录必须是绝对路径（docker.data.dir 或环境文件 FS_DATA_PATH）" ;;
    esac
    DATA_DIR="${DATA_DIR%/}"
    DATA_DIR="${DATA_DIR%\\}"
    case "$DATA_DIR" in
        ''|/|[A-Za-z]:) die "数据目录必须是专用的绝对目录，不能使用根目录" ;;
    esac

    execute "$ACTION"
}

main "$@"
