// Reminds, then unassigns, contributors who have gone quiet on an assigned issue.
// Run daily by .github/workflows/inactive-assignees.yml through actions/github-script.

const REMIND_AFTER_DAYS = 10;
const UNASSIGN_AFTER_DAYS = 14;
const REMINDER_MARKER = '<!-- flipjack:inactivity-reminder -->';
const DAY_MS = 24 * 60 * 60 * 1000;

const LINKED_PULL_REQUESTS = `
  query($owner: String!, $repo: String!, $number: Int!) {
    repository(owner: $owner, name: $repo) {
      issue(number: $number) {
        timelineItems(itemTypes: [CROSS_REFERENCED_EVENT, CONNECTED_EVENT], last: 50) {
          nodes {
            ... on CrossReferencedEvent { source { ... on PullRequest { state author { login } } } }
            ... on ConnectedEvent { subject { ... on PullRequest { state author { login } } } }
          }
        }
      }
    }
  }`;

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const now = Date.now();
  const maintainers = new Map();

  async function isMaintainer(login) {
    if (!maintainers.has(login)) {
      let result = false;
      try {
        const { data } = await github.rest.repos.getCollaboratorPermissionLevel({ owner, repo, username: login });
        result = data.permission === 'admin' || data.permission === 'write' || data.role_name === 'maintain';
      } catch {
        // not a collaborator
      }
      maintainers.set(login, result);
    }
    return maintainers.get(login);
  }

  async function hasOpenPullRequest(number, login) {
    const result = await github.graphql(LINKED_PULL_REQUESTS, { owner, repo, number });
    return result.repository.issue.timelineItems.nodes.some((node) => {
      const pr = node.source || node.subject;
      return pr && pr.state === 'OPEN' && pr.author && pr.author.login === login;
    });
  }

  const issues = await github.paginate(github.rest.issues.listForRepo,
    { owner, repo, state: 'open', assignee: '*', per_page: 100 });

  for (const issue of issues) {
    if (issue.pull_request) {
      continue;
    }
    const number = issue.number;
    const [events, comments] = await Promise.all([
      github.paginate(github.rest.issues.listEvents, { owner, repo, issue_number: number, per_page: 100 }),
      github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: number, per_page: 100 }),
    ]);

    for (const { login } of issue.assignees) {
      if (await isMaintainer(login) || await hasOpenPullRequest(number, login)) {
        continue;
      }

      // activity: when they were assigned, or their latest comment, whichever is later
      const assigned = events.filter((e) => e.event === 'assigned' && e.assignee && e.assignee.login === login).pop();
      const lastComment = comments.filter((c) => c.user.login === login).pop();
      const lastActivity = Math.max(
        Date.parse(assigned ? assigned.created_at : issue.created_at),
        lastComment ? Date.parse(lastComment.created_at) : 0);
      const idleDays = (now - lastActivity) / DAY_MS;
      const reminded = comments.some((c) => c.body.includes(REMINDER_MARKER) && Date.parse(c.created_at) > lastActivity);

      if (idleDays >= UNASSIGN_AFTER_DAYS) {
        await github.rest.issues.removeAssignees({ owner, repo, issue_number: number, assignees: [login] });
        await github.rest.issues.createComment({ owner, repo, issue_number: number,
          body: `@${login} there's been no activity here for ${UNASSIGN_AFTER_DAYS} days, so this issue `
            + `has been released for someone else. Thanks for your interest! If you'd still like to `
            + `work on it, comment \`/assign\` again while it's free.` });
        core.info(`#${number}: unassigned @${login} after ${Math.floor(idleDays)} idle days`);
      } else if (idleDays >= REMIND_AFTER_DAYS && !reminded) {
        await github.rest.issues.createComment({ owner, repo, issue_number: number,
          body: `${REMINDER_MARKER}\n@${login} just checking in: how's this going? If there's no `
            + `activity in the next ${UNASSIGN_AFTER_DAYS - REMIND_AFTER_DAYS} days, the issue will be `
            + `released for someone else. A comment here or an open pull request keeps it yours, `
            + `and \`/unassign\` releases it now.` });
        core.info(`#${number}: reminded @${login} after ${Math.floor(idleDays)} idle days`);
      }
    }
  }
};
