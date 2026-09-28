# 生产部署与备份手册（任务 6）

> 适用对象：PatchMe V3 内测部署。AI 负责实现，不拥有生产环境权限——服务器、域名、
> 密钥、备份的创建与保管全部由开发者本人完成（docs/v3/04）。
> 本机开发栈（`docker-compose.yml` + 宿主机 3307）与本手册互不影响。

## 0. 前置条件（开发者自己做）

| 项 | 要求 |
|---|---|
| 云服务器 | 一台 Linux（2C4G 起步），安装 Docker 与 Docker Com plugin |
| 域名 + HTTPS | A 记录解析到服务器；证书用 Caddy 自动签发或 certbot |
| 代码 | `git clone` 到服务器（推荐），或打包上传（不含 `.env`） |
| 生产 `.env` | 在服务器上从 `.env.example` 复制并填**全新**值：DB 密码、ROOT 密码、与开发环境**不同**的 `JWT_SECRET`（≥32 字节随机串）、`APP_CORS_ORIGINS=https://你的域名` |
| 文件权限 | `chmod 600 .env`；`.env` 永不进 Git（已在 .gitignore） |

## 1. 构建与启动

```bash
# 项目根目录
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # 三个服务均应为 healthy/running
curl -s http://127.0.0.1/api/health                 # 经 nginx 反代
curl -s http://127.0.0.1:8080/api/health            # 直连后端（调试用）
```

栈结构：`web`（nginx：SPA 静态资源 + `/api` 反代）→ `backend`（Spring Boot `prod` profile）
→ `mysql`（命名卷 `patchme_prod_mysql_data`）。Flyway 在启动时自动执行迁移，禁止手工改表。
镜像内 Maven 构建走 `backend/docker-settings.xml` 的 aliyun 镜像（本机与国内服务器直连 Central 过慢）。
日志卷 `patchme_prod_logs` 的属主由镜像内预建的 `/app/logs`（chown 给非 root 运行用户）决定，
不要删除 Dockerfile 里那两行，否则 root 属主的匿名卷会让应用在日志初始化时崩溃。

时区约定：`mysql` 容器 `--default-time-zone=+08:00`、`backend` 容器 `TZ=Asia/Shanghai`、
JDBC URL `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true` 三者同钟。
限流窗口（`moderation_log` 的 `DEFAULT CURRENT_TIMESTAMP` 对比 `LocalDateTime.now()`）依赖这一点，
任何一环改成别的时区都会导致风控偏移 8 小时。

## 2. HTTPS / Cookie 安全（上线闸门）

1. 外层 TLS：推荐 Caddy 反代 `web` 容器（自动续期），或 nginx + certbot。
   **没有 HTTPS 不要邀请真实用户**——Secure Cookie 无法生效，登录凭据明文过网。
2. `prod` profile 下 `APP_COOKIE_SECURE` 默认 `true`：`pm_access` 带 `HttpOnly + Secure + SameSite=Lax`。
3. CSRF 复审结论（SecurityConfig 遗留项）：token 只存 HttpOnly Cookie，跨站表单/脚本无法读取或
   主动携带；`SameSite=Lax` 下 POST 一律不带 Cookie，浏览器层已阻断 CSRF。内测接受现状；
   完全公开注册前再评估 double-submit 或 `SameSite=Strict`。
4. CORS：同源部署（页面与 `/api` 同域）时跨域请求实际不发生；`APP_CORS_ORIGINS` 仍必须精确
   到 `https://域名`，禁止 `*`、禁止残留 `localhost:5173`。
5. Swagger：`prod` 下 `springdoc.*.enabled=false`，后端对 `/swagger-ui.html`、`/v3/api-docs` 直接返回 404。
   注意：nginx 只反代 `/api/`，所以从域名访问这两个路径拿到的是 SPA 首页（200 text/html），**不是**文档泄露；
   正确验证法是进后端容器看真状态：`docker compose -f docker-compose.prod.yml exec backend wget -S -qO- http://127.0.0.1:8080/v3/api-docs` 应报 404。

## 3. 管理员一次性初始化（不允许注册即管理员）

注册接口只能产出 USER 角色。部署后由开发者在服务器上手工提升第一个管理员：

```bash
# 1) 在网页正常注册管理员账号（角色仍是 USER）
# 2) 一次性提升：
docker compose -f docker-compose.prod.yml exec mysql \
  mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
  -e "UPDATE users SET role='ADMIN' WHERE email='<管理员邮箱>'"
# 3) 该账号重新登录，导航出现"审核后台"即成功
```

日常授权/撤权在审核后台外不做批量操作；新增管理员重复本节步骤。数据库口令只存在于服务器 `.env`。

## 4. 数据库备份与恢复演练（上线前必须跑通一次）

```bash
# /usr/local/bin/patchme-backup.sh（服务器上创建，chmod +x）
set -euo pipefail
cd /opt/patchme                                  # compose.prod.yml 所在目录
TS=$(date +%F_%H%M)
docker compose -f docker-compose.prod.yml exec -T mysql \
  mysqldump -u"$DB_USERNAME" -p"$DB_PASSWORD" --single-transaction --routines "$DB_NAME" \
  | gzip > "/var/backups/patchme/patchme_$TS.sql.gz"
find /var/backups/patchme -name '*.sql.gz' -mtime +14 -delete
```

crontab（每日 03:30）+ **异地一份**（scp/rclone 到对象存储；本机盘丢失=全丢）：

```cron
30 3 * * * /usr/local/bin/patchme-backup.sh >> /var/log/patchme-backup.log 2>&1
```

恢复演练（上线前做一次，之后每季度一次）：

```bash
# 1) 起一个一次性 MySQL 容器（不影响生产卷）
docker run -d --name patchme-restore-drill -e MYSQL_ROOT_PASSWORD=drillonly mysql:8.4
# 2) 灌入最新备份
gunzip -c /var/backups/patchme/patchme_<时间戳>.sql.gz | \
  docker exec -i patchme-restore-drill mysql -uroot -pdrillonly
# 3) 校验关键表非空后销毁演练容器
docker exec patchme-restore-drill mysql -uroot -pdrillonly \
  -e "SELECT COUNT(*) FROM $DB_NAME.users; SELECT COUNT(*) FROM $DB_NAME.posts;"
docker rm -f patchme-restore-drill
```

备份文件含用户邮箱与内容原文：**等同生产库本身保管**——加密、限权限、不外发、不进 Git。

## 5. 日志与脱敏

- `prod` profile 启用 `logback-spring.xml`：控制台 + 文件双写，文件按天/50MB 滚动、
  gzip 归档、保留 30 天、总量 2GB 封顶；目录经卷 `patchme_prod_logs` 挂到 `/app/logs`。
- 所有消息过 `MaskingConverter`（`%mask`）：邮箱、password 字段、JWT、`pm_access=`、Bearer 头
  一律打码；异常堆栈保留但消息脱敏。单元测试 `MaskingConverterTest` 守规则。
- MyBatis 不输出 SQL 参数（prod 下 `com.patchme` 为 info）；访问日志在 nginx 层。
- 查看：`docker compose -f docker-compose.prod.yml logs -f backend` 或
  `docker compose ... exec backend ls /app/logs`。

## 6. 上线清单（docs/v3/04 逐项，自检后打勾）

- [ ] 云服务器/域名/数据库由开发者本人创建，AI 无生产权限
- [ ] 服务器 `.env` 独立强密钥；`JWT_SECRET` 与开发环境不同；`chmod 600`
- [ ] `git ls-files | grep -i env` 只有 `.env.example`；工作区无密钥文件被暂存
- [ ] HTTPS 生效（浏览器锁标志）；`APP_COOKIE_SECURE=true` 下重新登录成功
- [ ] `APP_CORS_ORIGINS` 精确到生产域名
- [ ] 后端容器内 `/swagger-ui.html`、`/v3/api-docs` 返回 404（经 nginx 看到的是 SPA 首页，见第 2 节第 5 条）
- [ ] Flyway 迁移全部成功（启动日志无 migration 错误）
- [ ] 备份脚本 + cron 就位，**恢复演练已完成一次**
- [ ] 管理员账号按第 3 节一次性初始化；重新注册一个账号确认无法自选 ADMIN
- [ ] 双账号全链路集成测试通过（本机 `mvn test`，Testcontainers 类不被跳过）
- [ ] 浏览器双账号回归通过：匿名零泄露 / 公开主页 / 删除 / 举报→审核 / 限制→恢复 / 通知仅本人
- [ ] 能每日处理举报与高风险队列（做不到就不要开放注册）
- [ ] 先邀请 20–50 人内测（发注册链接/口令即可，无公开入口），观察一周再评估开放注册

## 7. 升级与回滚

- 升级 = `git pull` + `docker compose -f docker-compose.prod.yml build && up -d`；
  表结构变更只能来自新增 Flyway `V{n}__` 脚本（只前进，不改历史）。
- 新版本异常：`docker compose ... up -d --scale backend=0 backend && docker compose ... up -d backend`
  可临时停写入；回滚代码后 Flyway 已应用的迁移**不回滚**（向后兼容列设计，旧代码跑新库）。
- 数据出错：优先用第 4 节备份在演练容器里核对，再决定是否恢复演练流程上生产。
- 出现 docs/v3/04「何时停止找审查」任一条（匿名泄露、越权、密钥入日志、迁移失败等）：
  立即停写、保住日志与备份，不要现场快速修补。

## 8. 内测节奏建议（20–50 人）

第 1 周 ≤10 人：验证注册/发帖/举报真实链路；第 2 周扩到 30–50：观察限流阈值与举报量；
每日固定时间清队列（待处理 + SYSTEM 风险送审）；通知与审核记录永远不暴露举报人。
出现刷屏/诈骗内容按 04 手册渐进处罚（提醒→观察→限制→封禁），封禁前人工核实。
