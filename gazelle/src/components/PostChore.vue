<template>
  <div>
    <div class="separator">
      <md-checkbox v-model="checked" class="checkbox">
        {{ chore.text }}
      </md-checkbox>
      <md-button
        class="md-icon-button toggleFocusButton"
        v-if="!checked"
        :class="{ 'md-primary': isFocused, 'md-raised': isFocused }"
        v-on:click="toggleFocus"
      >
        <md-icon>event_available</md-icon>
        <md-tooltip md-direction="right">
          {{ isFocused ? "Fjern fra fokusliste" : "Legg til i fokusliste" }}
        </md-tooltip>
      </md-button>
    </div>
    <md-divider></md-divider>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import { ChoreProgress, ChoreResponse } from "@/client/types";
import { setChoreState } from "@/client/chore";

@Component
export default class PostChore extends Vue {
  @Prop() private chore!: ChoreResponse;
  private checked = this.chore.progress === ChoreProgress.DONE;
  private isFocused = this.chore.progress === ChoreProgress.FOCUSED;

  private async toggleFocus() {
    this.isFocused = !this.isFocused;
    const progress: ChoreProgress = this.isFocused
      ? ChoreProgress.FOCUSED
      : ChoreProgress.UNDONE;
    await setChoreState(
      this.chore.id,
      this.$store.getters.loggedInUserId,
      progress
    );
  }

  @Watch("checked")
  async onCheckChange() {
    const progress: ChoreProgress = this.checked
      ? ChoreProgress.DONE
      : ChoreProgress.UNDONE;
    if (progress === ChoreProgress.UNDONE) this.isFocused = false;
    await setChoreState(
      this.chore.id,
      this.$store.getters.loggedInUserId,
      progress
    );
  }
}
</script>

<style scoped>
.separator {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.md-primary {
  --md-theme-default-primary: #f29253;
}
</style>
