#!/usr/bin/env bash
# manage.sh 行为测试：PATH 前置假 docker，断言实际下发的命令、退出码与错误文案。
# 全程不连接真实守护进程；仅在环境文件用例中临时创建 docker/.env.unix，结束即删除。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANAGE="$ROOT/docker/manage.sh"
FAKE_BIN="$ROOT/docker/tests/fake-bin"
ENV_CREATED=""
WORK="$(mktemp -d "${TMPDIR:-/tmp}/fs-manage-test-XXXXXX")"
trap 'rm -rf "$WORK"; [ -z "$ENV_CREATED" ] || rm -f "$ROOT/docker/.env.unix"' EXIT

PASS=0
FAIL=0
CASE_NO=0
CASE_DIR=""
DATA_DIR=""
FAKE_LOG=""
CASE_OUT=""
CASE_ERR=""
CASE_STATUS=0
WAIT_TIMEOUT=240
NO_DATA_DIR=""
# 留空时与被测模块同名（模拟 -N -f 单模块）；测试父 reactor 时显式设为父模块
ROOT_MODULE=""

ok() {
    PASS=$((PASS + 1))
    printf '  ok   %s\n' "$1"
}

bad() {
    FAIL=$((FAIL + 1))
    printf '  FAIL %s\n' "$1"
}

begin() {
    CASE_NO=$((CASE_NO + 1))
    CASE_DIR="$WORK/$CASE_NO"
    mkdir -p "$CASE_DIR"
    DATA_DIR="$CASE_DIR/data"
    FAKE_LOG="$CASE_DIR/docker.log"
    : > "$FAKE_LOG"
    export FAKE_LOG FAKE_DATA_DIR="$DATA_DIR"
    export FAKE_LEGACY_VOLUMES="" FAKE_CONTEXT_HOST="" FAKE_CONTEXT_LISTING=""
    export FAKE_INSPECT_ERROR="" FAKE_IMAGE_ID=""
    WAIT_TIMEOUT=240
    NO_DATA_DIR=""
    ROOT_MODULE=""
    printf '\n%s\n' "$1"
}

run() {
    local module="$1" action="$2"
    shift 2
    local args=(--module "$module" --root-module "${ROOT_MODULE:-$module}"
                --action "$action" --wait-timeout "$WAIT_TIMEOUT")
    [ -n "$NO_DATA_DIR" ] || args+=(--data-dir "$DATA_DIR")
    PATH="$FAKE_BIN:$PATH" bash "$MANAGE" "${args[@]}" "$@" \
        >"$CASE_DIR/stdout" 2>"$CASE_DIR/stderr"
    CASE_STATUS=$?
    CASE_OUT="$(cat "$CASE_DIR/stdout")"
    CASE_ERR="$(cat "$CASE_DIR/stderr")"
}

status_is() {
    if [ "$CASE_STATUS" = "$1" ]; then
        ok "$2"
    else
        bad "$2（退出码 ${CASE_STATUS}，期望 $1）"
    fi
}

err_has() {
    case "$CASE_ERR" in
        *"$1"*) ok "$2" ;;
        *) bad "$2（错误输出：${CASE_ERR}）" ;;
    esac
}

out_has() {
    case "$CASE_OUT" in
        *"$1"*) ok "$2" ;;
        *) bad "$2（输出：${CASE_OUT}）" ;;
    esac
}

log_has() {
    if grep -Fq -- "$1" "$FAKE_LOG"; then
        ok "$2"
    else
        bad "$2（未出现：$1）"
    fi
}

log_missing() {
    if grep -Fq -- "$1" "$FAKE_LOG"; then
        bad "$2（意外出现：$1）"
    else
        ok "$2"
    fi
}

begin "共享库没有独立 Docker 服务"
run auth-starter status
status_is 1 "auth-starter 被拒绝"
err_has "共享库" "提示共享库无服务"

begin "父 reactor 中的非根模块直接跳过"
ROOT_MODULE=fullstack-scaffold-backend
run web-vue up
status_is 0 "子模块短路退出码 0"
if [ -s "$FAKE_LOG" ]; then
    bad "短路时不应调用 docker"
else
    ok "短路时不应调用 docker"
fi

begin "--wait-timeout 必须是正整数"
WAIT_TIMEOUT=abc
run fullstack-scaffold-backend up
status_is 1 "非数字超时被拒绝"
err_has "必须是正整数" "提示超时格式错误"

begin "未知动作被拒绝"
run fullstack-scaffold-backend boom
status_is 1 "未知动作退出码 1"
err_has "不支持的动作：boom" "提示动作不受支持"

begin "父级 delete 只删容器与网络"
run fullstack-scaffold-backend delete
status_is 0 "父级 delete 成功"
log_has "down --remove-orphans" "下发 down --remove-orphans"
log_missing "--volumes" "不删除数据卷"
log_missing "image rm" "不删除镜像"

begin "子模块 stop/delete 只针对自身"
run web-vue stop
run web-vue delete
status_is 0 "stop/delete 成功"
if [ "$(grep -Fc -- 'stop web-vue' "$FAKE_LOG")" = "1" ]; then
    ok "stop 只带自身服务名"
else
    bad "stop 只带自身服务名"
fi
log_has "rm --stop --force web-vue" "delete 只删自身容器"
log_missing "down --remove-orphans" "子模块 delete 不走父级 down"

begin "create 只新建选中服务且不启动"
run web-vue create
status_is 0 "create 成功"
log_has "up --no-start --no-build --no-deps web-vue" "create 参数正确"

begin "父级 up 不重建也不强拉依赖"
run fullstack-scaffold-backend up
run fullstack-scaffold-backend up
status_is 0 "重复 up 成功"
if [ "$(grep -Fc -- 'up -d --no-build --wait --wait-timeout 240' "$FAKE_LOG")" = "2" ]; then
    ok "两次 up 均按收敛语义下发"
else
    bad "两次 up 均按收敛语义下发"
fi
log_missing "--force-recreate" "不使用 --force-recreate"
log_missing "--no-deps" "父级 up 不附加 --no-deps"
log_has "env FS_DATA_PATH=$DATA_DIR" "数据根由 docker.data.dir 决定"

begin "build 只构建选中镜像并过滤宿主产物"
FAKE_CONTEXT_LISTING="$CASE_DIR/context.txt"
export FAKE_CONTEXT_LISTING
run web-vue build
status_is 0 "build 成功"
log_has "--tag probe/web-vue:test" "使用 compose 解析出的镜像名"
log_missing "probe/gateway-service" "不构建未选中的服务"
if grep -q '/package\.json$' "$FAKE_CONTEXT_LISTING"; then
    ok "源码进入构建上下文"
else
    bad "源码进入构建上下文"
fi
if grep -q 'node_modules' "$FAKE_CONTEXT_LISTING"; then
    bad "构建上下文过滤 node_modules"
else
    ok "构建上下文过滤 node_modules"
fi
if grep -q '/dist$' "$FAKE_CONTEXT_LISTING"; then
    bad "构建上下文过滤 dist"
else
    ok "构建上下文过滤 dist"
fi
if grep -q '/\._' "$FAKE_CONTEXT_LISTING"; then
    bad "构建上下文过滤 ._* 文件"
else
    ok "构建上下文过滤 ._* 文件"
fi

begin "init 准备四个数据目录"
run fullstack-scaffold-backend init
status_is 0 "init 成功"
for directory in fs_mysql_data fs_redis_data fs_nacos_data fs_nacos_logs; do
    if [ -d "$DATA_DIR/$directory" ]; then
        ok "创建 $directory"
    else
        bad "创建 $directory"
    fi
done

begin "环境文件提供数据根，命令行可覆盖"
if [ -e "$ROOT/docker/.env.unix" ]; then
    bad "仓库已有 docker/.env.unix，跳过该用例以免覆盖"
else
    ENV_DATA="$CASE_DIR/env-data"
    printf '# 模板测试\nFS_DATA_PATH=%s\n' "$ENV_DATA" > "$ROOT/docker/.env.unix"
    ENV_CREATED=1
    NO_DATA_DIR=1
    run fullstack-scaffold-backend up
    status_is 0 "未传 --data-dir 时仍能启动"
    if [ -d "$ENV_DATA/fs_mysql_data" ]; then
        ok "数据根取自 .env.unix"
    else
        bad "数据根取自 .env.unix"
    fi
    log_has "--env-file $ROOT/docker/.env.unix" "加载平台环境文件"
    log_has "env FS_DATA_PATH=$ENV_DATA" "FS_DATA_PATH 同步传给 compose"
    rm -f "$ROOT/docker/.env.unix"
    ENV_CREATED=""
    NO_DATA_DIR=""
    : > "$FAKE_LOG"
    run fullstack-scaffold-backend up
    status_is 0 "传 --data-dir 时成功"
    log_has "env FS_DATA_PATH=$DATA_DIR" "命令行数据根覆盖环境文件"
fi

begin "存在旧命名卷时要求先迁移"
export FAKE_LEGACY_VOLUMES="fullstack-scaffold_fs-mysql-data"
run fullstack-scaffold-backend init
status_is 1 "旧卷存在时失败"
err_has "先执行" "提示先执行 migrate"
if [ -d "$DATA_DIR/fs_mysql_data" ]; then
    bad "拒绝时不动数据目录"
else
    ok "拒绝时不动数据目录"
fi

begin "迁移不覆盖非空目标目录"
export FAKE_LEGACY_VOLUMES="fullstack-scaffold_fs-mysql-data"
mkdir -p "$DATA_DIR/fs_mysql_data"
printf 'important\n' > "$DATA_DIR/fs_mysql_data/keep"
run fullstack-scaffold-backend migrate
status_is 1 "非空目标时失败"
err_has "拒绝覆盖" "提示拒绝覆盖"
if [ "$(cat "$DATA_DIR/fs_mysql_data/keep")" = "important" ]; then
    ok "既有数据保持不变"
else
    bad "既有数据保持不变"
fi
log_missing " stop" "校验失败前不停止服务"

begin "重复 init 不改动已有数据"
mkdir -p "$DATA_DIR/fs_mysql_data"
printf 'existing\n' > "$DATA_DIR/fs_mysql_data/keep"
run fullstack-scaffold-backend init
run fullstack-scaffold-backend init
status_is 0 "重复 init 成功"
if [ "$(cat "$DATA_DIR/fs_mysql_data/keep")" = "existing" ]; then
    ok "已有数据保持不变"
else
    bad "已有数据保持不变"
fi

begin "远程守护进程拒绝准备 bind 目录"
export FAKE_CONTEXT_HOST="ssh://server"
run fullstack-scaffold-backend init
status_is 1 "远程守护进程被拒绝"
err_has "服务器" "提示仅支持本机 socket"
if [ -d "$DATA_DIR/fs_mysql_data" ]; then
    bad "不在客户端建目录"
else
    ok "不在客户端建目录"
fi

begin "已完成的迁移再次执行不复制"
export FAKE_LEGACY_VOLUMES="fullstack-scaffold_fs-mysql-data fullstack-scaffold_fs-redis-data \
fullstack-scaffold_fs-nacos-data fullstack-scaffold_fs-nacos-logs"
mkdir -p "$DATA_DIR"
: > "$DATA_DIR/.migrated-mysql"
: > "$DATA_DIR/.migrated-redis"
: > "$DATA_DIR/.migrated-nacos-data"
: > "$DATA_DIR/.migrated-nacos-logs"
run fullstack-scaffold-backend migrate
status_is 0 "重复迁移成功"
out_has "无需重复迁移" "提示迁移已完成"
log_missing " stop" "不再停止服务"
if [ -d "$DATA_DIR/fs_nacos_data" ]; then
    ok "迁移完成后仍确保数据目录存在"
else
    bad "迁移完成后仍确保数据目录存在"
fi

begin "docker inspect 失败不等于卷缺失"
export FAKE_INSPECT_ERROR="permission denied while connecting to the Docker daemon"
run fullstack-scaffold-backend init
status_is 1 "inspect 异常时失败"
err_has "permission denied" "向上暴露原始错误"
if [ -d "$DATA_DIR/fs_mysql_data" ]; then
    bad "不把异常当成缺失后继续建目录"
else
    ok "不把异常当成缺失后继续建目录"
fi

begin "remove-image 只删存在的选中镜像"
export FAKE_IMAGE_ID="sha256:probe"
run web-vue remove-image
status_is 0 "remove-image 成功"
log_has "image rm probe/web-vue:test" "删除选中镜像"
log_missing "probe/gateway-service" "不影响其他镜像"

begin "status 与 config 不加锁也不动容器"
run web-vue status
run fullstack-scaffold-backend config
status_is 0 "status/config 成功"
log_has "ps -a web-vue" "status 只查状态"
log_has "config --quiet" "config 只做校验"
log_missing " up" "只读操作不创建容器"
if [ -d "$DATA_DIR/fs_mysql_data" ]; then
    bad "只读操作不建数据目录"
else
    ok "只读操作不建数据目录"
fi
if [ -e "$DATA_DIR/.lifecycle.lock.d" ]; then
    bad "只读操作不持锁"
else
    ok "只读操作不持锁"
fi

printf '\n通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
[ "$FAIL" = "0" ]
