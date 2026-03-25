<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  addFollowingUser,
  fetchFollowingUsers,
  removeFollowingUser
} from "../api";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const text = {
  adminTitle: "好友关系查看",
  userTitle: "好友关注",
  refresh: "刷新列表",
  helper: "关注好友后，系统会把对方的收藏和听歌历史作为社交信号纳入 NCF + Attention 推荐计算，用于降低冷启动和推荐单调问题。",
  inputUserKey: "输入用户 ID 或用户名",
  inputFriendKey: "输入好友ID或用户名",
  addFriend: "添加好友",
  needUserAdmin: "请输入用户 ID 或用户名后再查看好友关系。",
  needUserSelf: "未获取到当前用户信息，请重新登录。",
  needUserAdd: "请输入用户 ID 或用户名后再添加好友。",
  needFriendKey: "请输入好友 ID 或用户名。",
  addSuccess: "好友关注已更新，新的社交信号会参与推荐计算。",
  removeSuccess: "已取消关注该好友。",
  male: "男",
  female: "女",
  unknown: "未知",
  friendId: "好友 ID",
  username: "用户名",
  gender: "性别",
  birthYear: "出生年份",
  followTime: "关注时间",
  action: "操作",
  unfollow: "取消关注",
  emptyAdmin: "请输入用户 ID 或用户名后查看好友关系，或直接为该用户添加好友。",
  emptyUser: "你还没有关注好友，可以输入好友 ID 立即添加。"
};

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const route = useRoute();
const router = useRouter();
const admin = isAdminUser(resolveCurrentUsername());
const selfUserId = resolveCurrentUserId();

const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const targetUserKey = ref("");
const follows = ref([]);
const busy = ref(false);
const error = ref("");
const success = ref("");

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const formatDateTime = (value) => {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
};

const formatGender = (value) => {
  if (Number(value) === 1) {
    return text.male;
  }
  if (Number(value) === 2) {
    return text.female;
  }
  return text.unknown;
};

const loadFollows = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needUserAdmin : text.needUserSelf;
    follows.value = [];
    return;
  }

  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    follows.value = await fetchFollowingUsers(selectedUserId.value);
    if (admin) {
      router.replace({
        name: "home-friends",
        params: { userId: selectedUserId.value },
        query: { ...route.query }
      });
    }
  } catch (e) {
    follows.value = [];
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const addFollow = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needUserAdd : text.needUserSelf;
    return;
  }
  if (!targetUserKey.value.trim()) {
    error.value = text.needFriendKey;
    return;
  }

  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await addFollowingUser(selectedUserId.value, targetUserKey.value.trim());
    targetUserKey.value = "";
    success.value = text.addSuccess;
    await loadFollows();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const removeFollow = async (row) => {
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await removeFollowingUser(selectedUserId.value, String(row.followeeUserId));
    success.value = text.removeSuccess;
    follows.value = follows.value.filter((item) => item.followeeUserId !== row.followeeUserId);
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

watch(
  () => props.userId,
  async (value) => {
    syncSelectedUserId(value);
    if (selectedUserId.value) {
      await loadFollows();
    }
  }
);

onMounted(async () => {
  syncSelectedUserId(props.userId);
  if (selectedUserId.value) {
    await loadFollows();
  }
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ admin ? text.adminTitle : text.userTitle }}</h2>
      <button class="btn-secondary" :disabled="busy" type="button" @click="loadFollows">
        {{ text.refresh }}
      </button>
    </div>

    <p class="helper-text">{{ text.helper }}</p>

    <div class="inline-form">
      <input
        v-if="admin"
        v-model.trim="selectedUserId"
        :placeholder="text.inputUserKey"
      />
      <input
        v-model.trim="targetUserKey"
        :placeholder="text.inputFriendKey"
      />
      <button class="btn-primary" :disabled="busy" type="button" @click="addFollow">
        {{ text.addFriend }}
      </button>
    </div>

    <p v-if="success" class="helper-text">{{ success }}</p>
    <p v-if="error" class="error-text">{{ error }}</p>

    <table v-if="follows.length" class="ncf-table">
      <thead>
        <tr>
          <th>{{ text.friendId }}</th>
          <th>{{ text.username }}</th>
          <th>{{ text.gender }}</th>
          <th>{{ text.birthYear }}</th>
          <th>{{ text.followTime }}</th>
          <th>{{ text.action }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in follows" :key="`${row.followerUserId}-${row.followeeUserId}`">
          <td>{{ row.followeeUserId }}</td>
          <td>{{ row.followeeUsername || "-" }}</td>
          <td>{{ formatGender(row.followeeGender) }}</td>
          <td>{{ row.followeeBirthYear || "-" }}</td>
          <td>{{ formatDateTime(row.followTime) }}</td>
          <td>
            <button class="btn-danger mini" type="button" @click="removeFollow(row)">
              {{ text.unfollow }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <section v-else class="panel panel-empty">
      <p class="helper-text">
        {{ admin ? text.emptyAdmin : text.emptyUser }}
      </p>
    </section>
  </div>
</template>
