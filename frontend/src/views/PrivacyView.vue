<script setup lang="ts">
// 静态说明页：内容即承诺，所有条目与后端实际行为一一对应（docs/v3/01·02）。
</script>

<template>
  <article class="card privacy">
    <h1>隐私说明</h1>
    <h2>匿名机制</h2>
    <ul>
      <li>以「匿名」身份发布的内容，对外只展示「匿名」，不会带出昵称、头像、主页、学校、专业等任何身份信息。</li>
      <li>匿名内容发布后不可改为公开——平台和你一起守住这个承诺；公开内容可以随时改为匿名并立即从公开主页消失。</li>
      <li>匿名回复的站内提醒也不会透露回复者是谁。</li>
    </ul>
    <h2>数据可见范围</h2>
    <ul>
      <li>学校、专业仅用于内容筛选，不会出现在任何帖子、评论或主页的展示中。</li>
      <li>「我的」页面（帖子、回复、收藏、浏览历史、通知、设置）只有登录后的你自己可见，其他任何人（包括管理员入口开放前）都无法通过链接访问。</li>
      <li>公开主页只展示你当前以公开身份发布且未删除的内容。</li>
    </ul>
    <h2>浏览历史</h2>
    <ul>
      <li>登录后的浏览记录仅保留最近 30 天，且只有你自己能看到。</li>
      <li>可在「我的 → 账号设置」中关闭记录，或一键清空全部历史。</li>
    </ul>
    <h2>删除意味着什么</h2>
    <ul>
      <li>你删除帖子或回复后，内容立即从详情页、首页、主页与分享链接中消失，普通访客无法再看到原文。</li>
      <li>出于账号安全与追责的需要，数据库内的删除标记会保留，但不会再次出现在任何对外页面。</li>
    </ul>
    <h2>账号与登录</h2>
    <ul>
      <li>密码以不可逆的哈希形式存储，平台任何人员与代码都无法还原你的明文密码。</li>
      <li>登录状态保存在 HttpOnly Cookie 中，页面脚本读取不到，也不包含任何身份字段。</li>
    </ul>
  </article>
</template>

<style scoped>
.privacy {
  padding: 1.25rem 1.5rem;
}

.privacy h1 {
  font-size: 1.3rem;
  margin-top: 0;
}

.privacy h2 {
  font-size: 1rem;
  margin: 1.1rem 0 0.4rem;
}

.privacy ul {
  margin: 0;
  padding-left: 1.2rem;
}

.privacy li {
  font-size: 0.9rem;
  line-height: 1.8;
}
</style>
