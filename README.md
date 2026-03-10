# Bilibili Audio Service

一个基于 Spring Boot 的 Bilibili 视频转音频后台服务。

在网页中输入一个或多个 Bilibili 视频链接，系统会先解析分集，再将选中的内容提取为 MP3，任务完成后提供 ZIP 下载。

## 功能特性

- 支持输入一个或多个 Bilibili 视频链接
- 支持解析普通视频和多分集视频
- 分集列表支持分页展示
- 支持批量勾选分集后创建任务
- 任务列表支持分页、展开/折叠、状态统计和进度展示
- 后端并行处理音频提取和转码
- 下载 ZIP 后延迟自动清理任务文件
- 支持通过 Docker Compose 部署

## 技术栈

- Java 8
- Maven
- Spring Boot 2.7.18
- Thymeleaf
- yt-dlp
- ffmpeg

## 运行前提

本项目依赖以下外部工具：

- `yt-dlp`
- `ffmpeg`

本地运行前请确保它们已经安装，或者在启动参数中显式指定路径。

## 默认访问路径

当前项目已配置全局路径前缀：

- 应用前缀：`/bilibili-audio`

因此本地启动后的默认访问地址为：

- [http://127.0.0.1:8080/bilibili-audio/](http://127.0.0.1:8080/bilibili-audio/)

## 本地启动

### 1. 构建项目

```bash
mvn clean package -DskipTests
```

### 2. 启动服务

如果 `yt-dlp` 和 `ffmpeg` 已加入系统环境变量，可以直接运行：

```bash
java -jar target/bilibili-audio-service-0.0.1-SNAPSHOT.jar
```

如果需要显式指定工具路径，可以这样启动：

```bash
java -jar target/bilibili-audio-service-0.0.1-SNAPSHOT.jar \
  --app.media.ytDlpPath=/path/to/yt-dlp \
  --app.media.ffmpegPath=/path/to/ffmpeg
```

Windows 示例：

```powershell
java -jar target\bilibili-audio-service-0.0.1-SNAPSHOT.jar `
  --app.media.ytDlpPath=D:\tools\yt-dlp.exe `
  --app.media.ffmpegPath=D:\ffmpeg\bin\ffmpeg.exe
```

## 页面使用说明

1. 打开首页
2. 在文本框中粘贴一个或多个 Bilibili 视频链接，每行一个
3. 点击“解析链接”
4. 勾选要处理的分集
5. 点击“开始处理已选分集”
6. 等待任务完成后点击“下载 ZIP”

说明：

- 推荐输入纯链接，不要把标题、说明文字一起粘贴进去
- 多分集视频建议先解析后再选择具体分集
- 下载完成后，任务文件会按配置延迟自动清理

## 配置说明

配置文件位置：

- `src/main/resources/application.yml`

当前默认配置如下：

```yaml
server:
  port: 8080
  servlet:
    context-path: /bilibili-audio

app:
  media:
    ytDlpPath: yt-dlp
    ffmpegPath: ffmpeg
    workDir: work/tasks
  task:
    pollIntervalMs: 2000
    cleanupDelayMinutes: 10
```

主要配置项：

- `server.port`：服务端口
- `server.servlet.context-path`：应用访问前缀
- `app.media.ytDlpPath`：`yt-dlp` 可执行文件路径
- `app.media.ffmpegPath`：`ffmpeg` 可执行文件路径
- `app.media.workDir`：任务工作目录
- `app.task.pollIntervalMs`：前端轮询任务状态间隔
- `app.task.cleanupDelayMinutes`：下载后延迟清理任务文件的分钟数

## 核心接口

页面入口：

- `GET /bilibili-audio/`

任务接口：

- `POST /bilibili-audio/api/tasks/resolve-links`
- `POST /bilibili-audio/api/tasks`
- `GET /bilibili-audio/api/tasks/{taskId}`
- `GET /bilibili-audio/api/tasks/{taskId}/download`

## 工作目录说明

服务运行过程中会在工作目录下生成任务文件：

- `work/tasks/{taskId}/audio/`：生成的 MP3 文件
- `work/tasks/{taskId}/bundle/`：打包后的 ZIP 文件

当前逻辑为：

- 用户下载 ZIP 后，系统会延迟一段时间自动清理该任务目录
- 默认延迟时间为 `10` 分钟

## Docker Compose 部署

推荐部署方式：

- 本地打好 `jar`
- 服务器提前准备 `ffmpeg`、`ffprobe` 和 `yt-dlp` 可执行文件
- 通过 `docker-compose.yml` 将宿主机的 `jar`、`ffmpeg`、`ffprobe`、`yt-dlp` 挂载到容器内


## 当前限制

- 仅支持公开可访问的 Bilibili 视频
- 不处理登录态、会员专享、受限内容
- 输出格式固定为 MP3
- 下载方式固定为 ZIP 打包下载
- 依赖 `yt-dlp` 对页面和分集的解析能力
- Docker Compose 方案依赖宿主机上已经存在 `ffmpeg`、`ffprobe` 和 `yt-dlp`


## 免责声明

本项目仅供学习和技术研究使用。请在遵守目标平台服务条款、版权要求和当地法律法规的前提下使用。
