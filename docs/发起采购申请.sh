#!/usr/bin/env bash
# ============================================================================
#  ibpm 第三方发起接口调用脚本
# ----------------------------------------------------------------------------
#  用法：
#    bash 发起采购申请.sh              # 默认发起一笔，businessId 自动带时间戳
#    bash 发起采购申请.sh MY-BIZ-001   # 指定 businessId
#    bash 发起采购申请.sh MY-BIZ-001 1 # 再指定 initiatorUserId
#
#  说明：
#    - 签名算法为本项目实现，key 作为普通参数参与字典序拼接：
#        md5('accessKey=<ak>&key=<sk>&nonce=<nonce>&timestamp=<ts>')
#      （与 Sa-Token 官方"省略 sign 后追加 &key="的算法不同）
#    - 每次执行都会生成新的 timestamp 与 nonce，不会触发防重放。
#    - 本脚本只做一次真实发起，会在库里产生一条流程实例。
# ============================================================================

set -euo pipefail

# ---------------------------- 配置区（按需修改）----------------------------
HOST='http://127.0.0.1:8000'
FLOW_CODE='po_test1'
AK='MTIwOWUxN2Y4YzQ1NGJjYTgzODZiMm'
SK='Y2Y4YmY5MzgzYTkwNDllZDkwZTQyOWU1Mzk4YmIwMjM='

# 发起人：ibpm 系统用户 ID，必须是真实存在且未停用的用户（数字）
INITIATOR_USER_ID="${2:-1}"

# 业务单据 ID：第三方自己的单据号，必填，最长 40 字符
# 留空时自动用 THIRD-<时间戳> 生成，避免与历史单据重复
if [ -n "${1:-}" ]; then
  BUSINESS_ID="$1"
else
  BUSINESS_ID="THIRD-$(date +%Y%m%d%H%M%S)"
fi
# ---------------------------------------------------------------------------

# 逐一校验必填项，缺哪个直接说清楚，不靠服务端 400 报错
fail() { printf '\n[错误] %s\n\n' "$1" >&2; exit 1; }

[ -n "$AK" ] || fail 'AK 为空，请在脚本配置区填写 accessKey'
[ -n "$SK" ] || fail 'SK 为空，请在脚本配置区填写 secretKey'
[ -n "$BUSINESS_ID" ] || fail 'businessId 不能为空'
[ "${#BUSINESS_ID}" -le 40 ] || fail "businessId 超长（${#BUSINESS_ID} > 40）：$BUSINESS_ID"
case "$INITIATOR_USER_ID" in
  ''|*[!0-9]*) fail "initiatorUserId 必须是数字，当前值：$INITIATOR_USER_ID" ;;
esac

# ------------------------------ 生成签名 -----------------------------------
TS="$(date +%s%3N)"
NONCE="$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
# 字典序：accessKey < key < nonce < timestamp
SIGN="$(printf 'accessKey=%s&key=%s&nonce=%s&timestamp=%s' "$AK" "$SK" "$NONCE" "$TS" | md5sum | cut -d' ' -f1)"

# KEY 不打印，避免泄漏到终端记录
printf '\n========== 请求信息 ==========\n'
printf '地址          : %s/open/approval/instance\n' "$HOST"
printf 'flowCode      : %s\n' "$FLOW_CODE"
printf 'initiatorUserId: %s\n' "$INITIATOR_USER_ID"
printf 'businessId    : %s\n' "$BUSINESS_ID"
printf 'timestamp     : %s\n' "$TS"
printf 'nonce         : %s\n' "$NONCE"
printf 'sign          : %s\n' "$SIGN"

# ------------------------------ 请求体 -------------------------------------
# 类型要点（最容易踩的坑）：
#   po_qty  -> 数字，不加引号
#   keyItem -> 字符串 "1"/"0"，它是下拉框的实际 value，不是布尔
#
# 请求体先写入临时文件再用 curl -d @file 发送：
# Windows Git Bash 下直接 -d '<中文>' 常因编码/换行被破坏，
# 服务端会抛 HttpMessageNotReadableException 并回「参数缺失或格式不正确」。
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

cat > "$BODY_FILE" <<JSON
{
  "flowCode": "${FLOW_CODE}",
  "initiatorUserId": ${INITIATOR_USER_ID},
  "businessId": "${BUSINESS_ID}",
  "formData": {
    "po_num": "${BUSINESS_ID}",
    "po_person": "张三",
    "po_date": "$(date +%Y-%m-%d)",
    "remark": "第三方对接演示",
    "po_line": [
      { "item_name": "钢板", "item_code": "MAT-001", "po_qty": 10, "keyItem": "1" },
      { "item_name": "螺栓", "item_code": "MAT-002", "po_qty": 50, "keyItem": "0" }
    ]
  }
}
JSON

printf '\n========== 请求体 ==========\n'
cat "$BODY_FILE"

URL="${HOST}/open/approval/instance?accessKey=${AK}&timestamp=${TS}&nonce=${NONCE}&sign=${SIGN}"

printf '\n========== 响应 ==========\n'
# Content-Type 必须带 charset=utf-8，否则含中文的 body 可能被按其他编码解析
RESP="$(curl -s -w '\n%{http_code}' -X POST "$URL" \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary "@${BODY_FILE}")"

HTTP_CODE="$(printf '%s' "$RESP" | tail -n1)"
RESP_BODY="$(printf '%s' "$RESP" | sed '$d')"
printf '%s\n' "$RESP_BODY"
printf 'HTTP 状态码: %s\n' "$HTTP_CODE"

# ------------------------------ 结果判定 -----------------------------------
if printf '%s' "$RESP_BODY" | grep -q '"success":true'; then
  INSTANCE_ID="$(printf '%s' "$RESP_BODY" | sed -n 's/.*"instanceId":"\([^"]*\)".*/\1/p')"
  printf '\n[成功] 审批已发起，instanceId = %s\n\n' "$INSTANCE_ID"
else
  printf '\n[失败] 请对照上面响应中的 msg 排查：\n'
  printf '  accessKey无效              -> AK 与页面「应用详情」里的值不一致，重新复制\n'
  printf '  此 nonce 已被使用过        -> 每次执行脚本会自动换新 nonce，不要复用手工拼的 URL\n'
  printf '  无效签名                   -> SK 不对，或 timestamp 已超出时效窗口\n'
  printf '  参数缺失或格式不正确       -> body 未被解析（编码/换行损坏）或 Content-Type 不对；\n'
  printf '                                也可能是 initiatorUserId 非数字、businessId 为空/超 40 字符\n'
  printf '  参数 xxx 类型不匹配        -> 该字段类型写错（如 po_qty 加了引号、keyItem 写成布尔）\n'
  printf '  流程 [xxx] 不可发起        -> 该流程未发布 / 未绑定已发布表单 / 已暂停\n\n'
  exit 1
fi
