<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchItems, fetchUsers } from "../api";

const router = useRouter();

const users = ref([]);
const userTotal = ref(0);
const userPage = ref(1);
const userSize = ref(10);

const items = ref([]);
const itemTotal = ref(0);
const itemPage = ref(1);
const itemSize = ref(10);
const itemGenre = ref("");

const busy = ref(false);
const error = ref("");

const loadUsers = async () => {
  const pageData = await fetchUsers(userPage.value, userSize.value);
  users.value = pageData.list;
  userTotal.value = pageData.total;
};

const loadItems = async () => {
  const pageData = await fetchItems({
    genre: itemGenre.value || undefined,
    page: itemPage.value,
    size: itemSize.value
  });
  items.value = pageData.list;
  itemTotal.value = pageData.total;
};

const loadAll = async () => {
  busy.value = true;
  error.value = "";
  try {
    await Promise.all([loadUsers(), loadItems()]);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const goRecommendations = (userId) => {
  router.push({
    name: "home-recommendations",
    params: { userId },
    query: { from: "library" }
  });
};

const goHistory = (userId) => {
  router.push({
    name: "home-history",
    params: { userId },
    query: { from: "library" }
  });
};

onMounted(loadAll);
</script>

<template>
  <div class="page-grid">
    <p v-if="error" class="error-text">{{ error }}</p>

    <section class="panel">
      <div class="section-head">
        <h2>用户列表</h2>
        <div class="inline-form">
          <input v-model.number="userPage" min="1" placeholder="页码" type="number" />
          <input v-model.number="userSize" min="1" placeholder="每页条数" type="number" />
          <button class="btn-secondary" :disabled="busy" type="button" @click="loadUsers">刷新用户</button>
        </div>
      </div>
      <p class="helper-text">当前共 {{ userTotal }} 位用户</p>
      <table v-if="users.length" class="ncf-table">
        <thead>
          <tr>
            <th>用户 ID</th>
            <th>用户名</th>
            <th>性别</th>
            <th>出生年份</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.userId">
            <td>{{ user.userId }}</td>
            <td>{{ user.externalUserNo }}</td>
            <td>{{ user.gender === 1 ? "男" : user.gender === 2 ? "女" : "未知" }}</td>
            <td>{{ user.birthYear || "-" }}</td>
            <td class="table-actions-row">
              <button class="btn-primary mini" type="button" @click="goRecommendations(user.userId)">查看推荐</button>
              <button class="btn-secondary mini" type="button" @click="goHistory(user.userId)">听歌历史</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <div class="section-head">
        <h2>曲库列表</h2>
        <div class="inline-form">
          <input v-model.trim="itemGenre" placeholder="曲风筛选，例如 POP" />
          <input v-model.number="itemPage" min="1" placeholder="页码" type="number" />
          <input v-model.number="itemSize" min="1" placeholder="每页条数" type="number" />
          <button class="btn-secondary" :disabled="busy" type="button" @click="loadItems">刷新曲库</button>
        </div>
      </div>
      <p class="helper-text">当前共 {{ itemTotal }} 首歌曲</p>
      <table v-if="items.length" class="ncf-table">
        <thead>
          <tr>
            <th>歌曲 ID</th>
            <th>歌曲名</th>
            <th>歌手</th>
            <th>专辑</th>
            <th>曲风</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.itemId">
            <td>{{ item.itemId }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.artistName }}</td>
            <td>{{ item.albumName || "-" }}</td>
            <td>{{ item.genreCode || "-" }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>
