# SetLobbySpawn 使用说明

一个简单的全服出生点 + 入服欢迎插件（Paper 1.21.11 兼容，api-version 1.21）。

## 安装

1. 把 `setlobbyspawn1.21.11paper1.0.jar` 放入服务端 `plugins` 文件夹。
2. 重启服务器（或使用 PlugMan 等插件热加载）。
3. 首次启动后自动生成配置文件：`plugins/SetLobbySpawn/config.yml`。
4. 默认配置文件修改后执行 `/sls reload` 立即生效，无需重启。

## 指令

| 指令 | 说明 |
| --- | --- |
| `/sls spawn true` | 站在出生点执行：把当前位置设为全服出生点，模式 true = 每次入服、死亡后都回到此处 |
| `/sls spawn false` | 站在出生点执行：仅第一次入服的玩家出生在此，死亡回床/世界出生点 |
| `/sls spawn` | 查看当前出生点设置（世界、坐标、模式） |
| `/sls saywelcome <行号1-5> <内容>` | 设置入服欢迎语，支持 `&` 颜色符（如 `&6`），内容含空格直接输入即可 |
| `/sls welcometitle <行号1-5> <内容>` | 设置入服欢迎标题，第 1 行为主标题（最大），其余行依次减小 |
| `/sls reload` | 修改配置文件后重载，立即生效 |
| `/sls help` | 查看指令帮助 |

> 兼容写法：`/sls saywecome`（少一个 l）也能用。
> 用 `""` 作内容可清空对应行，例如 `/sls saywelcome 2 ""`。

权限：`setlobbyspawn.admin`（默认 OP 拥有）。

## 配置文件

```yaml
spawn:
  enabled: true      # true=每次入服/死亡后回出生点；false=仅第一次入服
  world: world       # 出生点所在世界
  x: 0.0             # 坐标，也可以用 /sls spawn <true|false> 直接设置
  y: 64.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0

welcome:
  lines:             # 欢迎语，最多 5 行，支持 & 颜色符，空行不显示
    - "&6欢迎来到本服务器！"
    - "&e输入 /sls help 查看指令帮助"

welcome-title:
  lines:             # 欢迎标题，最多 5 行；第1行=主标题(最大)，其余行依次减小
    - "&6欢迎加入"
    - "&eWelcome!"
```

> 提示：使用 `/sls spawn`、`/sls saywelcome` 等游戏内指令修改配置后，配置文件中的注释行可能被移除，但所有设置项和值不受影响，仍可直接编辑。
