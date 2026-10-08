-- 拾词输入法 · 译词注入 Lua filter（路线 A2）
-- 作用：候选已带 comment 时原样透传；未带注释时按本地词表补一条英文。
-- 前置：Trime 编译了 librime-lua（app/src/main/jni/cmake/Rime.cmake 里
--       RIME_PLUGINS 已包含 librime-lua，默认满足）。
-- 挂载：schema 的 engine/filters 加一行
--         - lua_filter@*pickword_comment@pw_gloss
-- 数据：pickword_gloss.lua，由
--         python tools/cedict_to_rime.py --format lua --out rime/lua/pickword_gloss.lua
--       生成，内容为 return { ["电脑"] = "computer", ... }
-- 开关：schema 的 switches 里定义 show_gloss，关闭时本 filter 直接放行。

local M = {}

local dict = nil      -- 词表：{ [中文] = 英文 }
local inited = false

-- 读取 schema 中 pw_gloss/data_file 指定的数据文件
local function load_dict(env)
  if inited then
    return
  end
  inited = true

  local name = "pickword_gloss"
  if env.engine and env.engine.schema then
    local cfg = env.engine.schema.config
    if cfg then
      name = cfg:get_string(env.name_space .. "/data_file") or name
    end
  end

  -- 优先 require（librime-lua 通常把 lua 目录放进 package.path）
  local ok, chunk = pcall(require, name)
  if ok and type(chunk) == "table" then
    dict = chunk
    return
  end

  -- 退回 dofile：拼出 lua 目录下的绝对路径
  local base = nil
  if env.engine and env.engine.schema then
    -- 不同版本属性名可能不同，取不到就跳过
    local ok_dir, dir = pcall(function()
      return env.engine.schema.user_data_dir or env.engine.schema.shared_data_dir
    end)
    if ok_dir and type(dir) == "string" then
      base = dir
    end
  end
  if base then
    local f = io.open(base .. "/lua/" .. name .. ".lua", "r")
    if f then
      local src = f:read("*a")
      f:close()
      local fn = load(src)
      if fn then
        local ok2, tbl = pcall(fn)
        if ok2 and type(tbl) == "table" then
          dict = tbl
        end
      end
    end
  end
end

function M.init(env)
  load_dict(env)
end

function M.func(input, env)
  local ctx = env and env.engine and env.engine.context
  if ctx and not ctx:get_option("show_gloss") then
    -- 译词开关关闭：原样透传，零开销
    for cand in input:iter() do
      yield(cand)
    end
    return
  end

  local max_len = 48
  local ok_len, n = pcall(function()
    return env.engine.schema.config:get_int(env.name_space .. "/max_len")
  end)
  if ok_len and type(n) == "number" and n > 0 then
    max_len = n
  end

  for cand in input:iter() do
    local gloss = nil
    local comment = cand.comment
    if (comment == nil or comment == "") and dict then
      gloss = dict[cand.text]
    end

    if gloss then
      if #gloss > max_len then
        gloss = gloss:sub(1, max_len - 1) .. "…"
      end
      -- 构造新候选：保留原 type 与选词区间，只替换注释
      yield(Candidate(cand.type, cand.start, cand._end, cand.text, gloss))
    else
      yield(cand)
    end
  end
end

function M.fini(env)
  dict = nil
  inited = false
end

return M
