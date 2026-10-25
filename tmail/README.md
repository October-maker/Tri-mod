# Tmail 远端模组资讯

这个目录用于存放 Tmail「获取最新资讯」功能读取的远端文件。  
`TriModInfo` 会从以下 raw 地址读取：

```text
https://raw.githubusercontent.com/October-maker/Tri-mod/main/tmail/manifest.json
```

> 当前代码假定默认分支是 `main`。如果仓库默认分支不是 `main`，需要同步修改  
> `src/triangle/black/TMail/TriModInfo.java` 中的 `RAW_BASE`。

## 目录结构

```text
tmail/
  manifest.json
  letters/
    <letter-id>/
      en.json
      zh_CN.json
      ...其他语言.json
```

`letters/<letter-id>/` 的目录名可以自由命名，但建议与 `manifest.json` 中的 `id` 保持一致。

## manifest.json

```json
{
  "schema": 1,
  "version": "1.0.0",
  "letters": [
    {
      "id": "info_about",
      "path": "letters/info_about"
    }
  ]
}
```

字段说明：

- `id`：邮件稳定标识，最终邮件键为 `modinfo-<id>`；
- `path`：相对于 `tmail/` 的目录路径，不要包含 `..` 或开头的 `/`；
- `letters` 的数组顺序就是 Tmail 资讯页的显示顺序。

### 增加一封资讯

1. 在 `tmail/letters/` 下新建目录，例如 `changelog`；
2. 添加 `en.json`、`zh_CN.json` 等语言文件；
3. 在 `manifest.json` 的 `letters` 数组中加入：

```json
{
  "id": "changelog",
  "path": "letters/changelog"
}
```

### 删除一封资讯

从 `manifest.json` 的 `letters` 数组中删除对应条目即可。  
下次刷新后，这封资讯会从资讯页移除。

> `id` 修改后会被视为一封新邮件，旧的已读状态不会继承。

## 语言文件

每封资讯目录下按语言放置 JSON：

```text
letters/info_about/
  en.json
  zh_CN.json
  zh.json        （可选）
```

读取顺序：

1. 当前游戏语言的完整 locale，例如 `zh_CN.json`；
2. 当前语言短码，例如 `zh.json`；
3. `en.json`。

如果三个文件都不存在，该封资讯会被跳过，不会影响其他资讯。

### 字段

```json
{
  "sender": "October-maker",
  "subject": "关于TMail",
  "body": "第一行\n第二行"
}
```

- `sender`：发件人；
- `subject`：主题；
- `body`：正文，使用 `\n` 表示换行；
- JSON 需要是合法 UTF-8。

## 刷新行为

- 打开 Tmail 的「模组资讯」页面时，如果距上次刷新超过 5 分钟，会自动请求最新版本；
- 点击资讯页的「获取最新资讯」按钮会强制刷新；
- 该按钮只在模组资讯页面显示，不会出现在战役剧情页面；
- 只有 manifest 中全部信件都成功加载时才会替换现有资讯；任意一封失败则保留旧内容；
- 请求失败时保留现有资讯，不会清空；手动刷新失败会显示提示；
- 刷新成功后，远端资讯会替换内置的资讯邮件，并立即重建资讯列表。

## 本地缓存

成功获取的资讯会按语言缓存到：

```text
<Mindustry 数据目录>/tmail/tmail-modinfo-cache-<locale>.json
```

例如：

```text
tmail-modinfo-cache-zh_CN.json
tmail-modinfo-cache-en.json
```

缓存按语言保存。切换语言后，下次打开资讯页会重新请求对应语言。

## 当前内置兜底

在远端文件尚未生效或网络不可用时，游戏仍会显示原有的 `info_about` 内置邮件。  
因此可以先推送此目录，再逐步修改内容。

## 推送步骤

```bash
git add tmail
git commit -m "Add Tmail remote mod info"
git push
```

推送后确认以下地址可以访问：

```text
https://raw.githubusercontent.com/October-maker/Tri-mod/main/tmail/manifest.json
```

如果该地址返回 404，请检查：

1. 文件是否确实在仓库根目录的 `tmail/` 下；
2. 分支是否为 `main`；
3. 文件名大小写是否完全一致。
