<script setup lang="ts">
// 社区公约静态页：与产品规则同源（docs/v3/01）。
</script>

<template>
  <article class="card rules">
    <h1>社区公约</h1>
    <h2>这里是什么地方</h2>
    <p>「人生补丁包」是大学生之间的匿名互助社区：说说正在卡住你的事，也帮别人补上他们缺的那一块。</p>
    <h2>三种帖子</h2>
    <ul>
      <li><strong>倾诉</strong>：只是想被听见，不需要答案。</li>
      <li><strong>求建议</strong>：把问题讲清楚，等一个过来人的经验。</li>
      <li><strong>找同路人</strong>：看看有没有人和你走在同一条路上。</li>
    </ul>
    <h2>请务必做到</h2>
    <ul>
      <li>不发布能识别他人身份的信息（真实姓名、学号、照片、联系方式等）。</li>
      <li>不评判、不嘲讽他人的处境；反驳观点，不攻击的人。</li>
      <li>涉及心理危机或人身安全时，优先建议寻求学校心理中心或专业机构的帮助。</li>
    </ul>
    <h2>你可以随时</h2>
    <ul>
      <li>删除自己的任何内容，删除后立即对外消失。</li>
      <li>把公开内容改成匿名，改后立即从公开主页移除，且不可再改回公开。</li>
      <li>楼主可以关闭某个帖子的评论，也可以把某条回复标记为「有帮助」。</li>
    </ul>
  </article>
</template>

<style scoped>
.rules {
  padding: 1.25rem 1.5rem;
}

.rules h1 {
  font-size: 1.3rem;
  margin-top: 0;
}

.rules h2 {
  font-size: 1rem;
  margin: 1.1rem 0 0.4rem;
}

.rules p,
.rules li {
  font-size: 0.9rem;
  line-height: 1.8;
}

.rules ul {
  margin: 0;
  padding-left: 1.2rem;
}
</style>
