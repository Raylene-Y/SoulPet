package online.raylene.pocketpet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val dp get() = resources.displayMetrics.density
    private fun Int.dp() = (this * dp).toInt()

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 28f * dp
    }

    /** 蓝色胶囊按钮 */
    private fun pillButton(label: String, filled: Boolean, onClick: (TextView) -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
            if (filled) {
                setTextColor(Color.WHITE)
                background = pill(ChatActivity.SLIME_BLUE)
            } else {
                setTextColor(ChatActivity.SLIME_DEEP)
                background = pill(Color.WHITE)
            }
            setOnClickListener { onClick(this) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TtsManager.warmUp(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                "com.termux.permission.RUN_COMMAND"
            ), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 60.dp(), 24.dp(), 24.dp())
            setBackgroundColor(ChatActivity.BG_SOFT)
        }

        // 大史莱姆
        val slime = ImageView(this).apply {
            setImageBitmap(SlimeFrames.draw(SlimeFrames.Pose.HAPPY, 8))
            layoutParams = LinearLayout.LayoutParams(140.dp(), 140.dp())
        }
        root.addView(slime)

        val title = TextView(this).apply {
            text = "SoulPet"
            textSize = 26f
            setTextColor(ChatActivity.INK)
            setPadding(0, 16.dp(), 0, 4.dp())
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "住在你屏幕上的小东西"
            textSize = 14f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 0, 0, 28.dp())
            gravity = Gravity.CENTER
        }
        root.addView(title); root.addView(sub)

        // 召唤按钮（主按钮）
        val summon = pillButton(if (PetService.running) "收起来" else "召唤它", true) { btn ->
            if (PetService.running) {
                stopService(Intent(this, PetService::class.java))
                btn.text = "召唤它"
            } else {
                summonPet()
                btn.text = "收起来"
            }
        }
        summon.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        summon.textSize = 17f
        summon.setPadding(0, 16.dp(), 0, 16.dp())
        root.addView(summon)

        // ── 皮肤 ──
        val skinTitle = TextView(this).apply {
            text = "皮肤"
            textSize = 13f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(4.dp(), 24.dp(), 0, 8.dp())
        }
        root.addView(skinTitle)
        val skinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(skinRow)
        renderSkins(skinRow)

        // ── 高级设置（折叠） ──
        val advancedBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
        }
        val advToggle = TextView(this).apply {
            text = "▸ 高级设置"
            textSize = 13f
            setTextColor(ChatActivity.INK_LIGHT)
            setPadding(4.dp(), 28.dp(), 0, 8.dp())
            setOnClickListener {
                val show = advancedBox.visibility == android.view.View.GONE
                advancedBox.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
                text = if (show) "▾ 高级设置" else "▸ 高级设置"
            }
        }
        root.addView(advToggle)

        // 高级项：模型设置 / 命令回传授权 / Termux 桥测试
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(pillButton("模型设置", false) { showLlmSettings() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 0, 8.dp(), 0) }
        })
        row1.addView(pillButton("命令回传授权", false) { grantStorage() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        advancedBox.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(pillButton("测试 Termux 桥", false) { testTermuxBridge() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 8.dp(), 8.dp(), 0) }
        })
        row2.addView(pillButton("导出宠物", false) { exportPet() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 8.dp(), 0, 0) }
        })
        advancedBox.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(pillButton("导入宠物（克隆）", false) { importPet() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 8.dp(), 8.dp(), 0) }
        })
        row3.addView(pillButton("它的记忆", false) { showMemoryPage() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(0, 8.dp(), 0, 0) }
        })
        advancedBox.addView(row3)

        root.addView(advancedBox)
        setContentView(root)

        // 有权限且未在跑 → 直接召唤，并同步按钮文字
        if (!PetService.running && Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, PetService::class.java))
            summon.postDelayed({ summon.text = "收起来" }, 500)
        }
    }

    private fun renderSkins(row: LinearLayout) {
        row.removeAllViews()
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        val current = prefs.getString("pet_id", "slime") ?: "slime"
        val skins = mutableListOf("slime" to "史莱姆")
        PetPackage.scanAll(java.io.File(filesDir, "pet/pets")).forEach { skins.add(it.id to it.displayName) }
        for ((id, label) in skins) {
            val chip = pillButton((if (id == current) "✓ " else "") + label, id == current) {
                prefs.edit().putString("pet_id", id).apply()
                if (PetService.running) stopService(Intent(this, PetService::class.java))
                startForegroundService(Intent(this, PetService::class.java))
                recreate()
            }
            chip.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 8.dp(), 0)
            }
            row.addView(chip)
        }
    }

    private fun grantStorage() {
        if (android.os.Environment.isExternalStorageManager()) {
            Toast.makeText(this, "已授权", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        }
    }

    private fun testTermuxBridge() {
        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/termux-notification")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-t", "桥测试", "-c", "看到这条说明桥通了"))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        val r = try {
            startService(i); "已发送，看通知栏有没有【桥测试】通知"
        } catch (e: Exception) { "失败：${e.message}" }
        android.app.AlertDialog.Builder(this)
            .setTitle("桥测试结果").setMessage(r).setPositiveButton("好", null).show()
    }

    private data class Preset(val name: String, val baseUrl: String, val model: String, val signup: String)

    private val presets = listOf(
        Preset("智谱 GLM", "https://api.z.ai/api/paas/v4/", "glm-4.5-flash", "https://open.bigmodel.cn/"),
        Preset("DeepSeek", "https://api.deepseek.com/", "deepseek-chat", "https://platform.deepseek.com/"),
        Preset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/", "qwen-flash", "https://bailian.console.aliyun.com/"),
        Preset("Kimi", "https://api.moonshot.cn/v1/", "moonshot-v1-8k", "https://platform.moonshot.cn/")
    )

    private fun showLlmSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }
        val etUrl = EditText(this).apply {
            hint = "Base URL（OpenAI 兼容接口）"; setText(LlmConfig.baseUrl(this@MainActivity))
        }
        val etKey = EditText(this).apply {
            hint = if (LlmConfig.apiKey(this@MainActivity).isNotBlank())
                "API Key（已配置，留空保持不变）" else "API Key"
        }
        val etModel = EditText(this).apply {
            hint = "模型名，如 glm-4.5-flash / deepseek-chat"; setText(LlmConfig.model(this@MainActivity))
        }

        // 预设服务商：一键填 baseurl+模型，点名字跳注册页
        box.addView(TextView(this).apply {
            text = "① 选一个服务商（自动填地址和模型名，长按跳注册页领 key）"
            textSize = 12f; setTextColor(ChatActivity.INK_LIGHT)
        })
        val presetGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0..1) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val p = presets[r * 2 + c]
                val b = pillButton(p.name, false) {
                    etUrl.setText(p.baseUrl); etModel.setText(p.model)
                    etKey.requestFocus()
                    Toast.makeText(this, "已填入 ${p.name}，粘上 API Key 就行", Toast.LENGTH_SHORT).show()
                }
                b.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    setMargins(if (c == 0) 0 else 4.dp(), 4.dp(), if (c == 0) 4.dp() else 0, 4.dp())
                }
                b.setOnLongClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.signup))); true
                }
                rowLayout.addView(b)
            }
            presetGrid.addView(rowLayout)
        }
        box.addView(presetGrid)
        box.addView(TextView(this).apply {
            text = "② 粘贴 API Key"
            textSize = 12f; setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 4.dp(), 0, 0)
        })
        box.addView(etKey)
        box.addView(TextView(this).apply {
            text = "③ 高级（一般不用改）"
            textSize = 12f; setTextColor(ChatActivity.INK_LIGHT)
            setPadding(0, 8.dp(), 0, 0)
        })
        box.addView(etUrl); box.addView(etModel)
        android.app.AlertDialog.Builder(this)
            .setTitle("模型设置")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                LlmConfig.save(this,
                    etUrl.text.toString().ifBlank { LlmConfig.baseUrl(this) },
                    etKey.text.toString().ifBlank { LlmConfig.apiKey(this) },
                    etModel.text.toString().ifBlank { LlmConfig.model(this) })
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 它的记忆：等级 + 人格坐标 + 记忆条目（可删） */
    private fun showMemoryPage() {
        LlmClient.init(applicationContext)
        val store = MemoryStore(applicationContext)
        val (lv, lvTitle) = LlmClient.levelInfo()
        val lines = store.memoryLines()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 0)
        }
        box.addView(TextView(this).apply {
            text = "亲密度 Lv.$lv $lvTitle（聊过 ${store.msgCount()} 轮）\n性格  ${Personality.renderForUI()}"
            textSize = 13f
        })
        box.addView(TextView(this).apply {
            text = "\n它记住的事（点一条可让它忘掉）："
            textSize = 12f
        })
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle("它的记忆")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("好", null)
            .create()
        if (lines.isEmpty()) {
            box.addView(TextView(this).apply { text = "（还没记住什么，多聊聊）"; textSize = 13f })
        } else {
            for (line in lines) {
                box.addView(TextView(this).apply {
                    text = line
                    textSize = 13f
                    setPadding(0, 8.dp(), 0, 8.dp())
                    setOnClickListener {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setMessage("让它忘掉这条？\n\n$line")
                            .setPositiveButton("忘掉") { _, _ ->
                                val cur = store.memoryLines()
                                cur.remove(line)
                                store.rewriteMemory(cur)
                                dlg.dismiss()
                                showMemoryPage()
                            }
                            .setNegativeButton("留着", null)
                            .show()
                    }
                })
            }
        }

        // 技能区
        val skills = store.skillLines()
        box.addView(TextView(this).apply {
            text = "\n它学会的技能（点一条可让它遗忘）："
            textSize = 12f
        })
        if (skills.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "（还没技能。聊天里说：记住，我说'拍照'就是 termux-camera-photo …）"
                textSize = 13f
            })
        } else {
            for (s in skills) {
                box.addView(TextView(this).apply {
                    text = s
                    textSize = 13f
                    setPadding(0, 8.dp(), 0, 8.dp())
                    setOnClickListener {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setMessage("让它遗忘这个技能？\n\n$s")
                            .setPositiveButton("遗忘") { _, _ ->
                                val cur = store.skillLines()
                                cur.remove(s)
                                store.rewriteSkills(cur)
                                dlg.dismiss()
                                showMemoryPage()
                            }
                            .setNegativeButton("留着", null)
                            .show()
                    }
                })
            }
        }
        dlg.show()
    }

    /** 导出：打包到缓存目录 → 系统分享（注意：分享的是全部记忆，提醒用户） */
    private fun exportPet() {
        android.app.AlertDialog.Builder(this)
            .setTitle("导出宠物")
            .setMessage("会打包它的全部：记忆、人格、性格坐标、任务、梦境。\n\n⚠️ 包含你的聊天记忆，只发给信任的人。")
            .setPositiveButton("导出") { _, _ ->
                val out = java.io.File(cacheDir, "soulpet-clone.zip")
                if (PetClone.export(this, out)) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "$packageName.fileprovider", out)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "把宠物发给…"))
                } else Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importPet() {
        android.app.AlertDialog.Builder(this)
            .setTitle("导入宠物")
            .setMessage("选择别人发你的 soulpet-clone.zip。\n\n⚠️ 会覆盖你现在宠物的全部记忆和人格！")
            .setPositiveButton("选文件") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, 42)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK && data?.data != null) {
            val msg = PetClone.import(this, data.data!!)
            android.app.AlertDialog.Builder(this).setMessage(msg).setPositiveButton("好", null).show()
        }
    }

    private fun summonPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, PetService::class.java))
    }
}
