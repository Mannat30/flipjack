// Handles /assign and /unassign comments on issues, and points people who ask in plain
// words ("can I work on this?") to /assign.
// Run by .github/workflows/issue-assignment.yml through actions/github-script.

const INACTIVITY_DAYS = 14;

// /assign or /unassign anywhere in the first line, optionally followed by @user
const COMMAND = /(?:^|\s)(\/(?:un)?assign)\b(?:\s+(@[\w-]+))?/i;

// plain-language requests to take an issue; only ever answered with a hint, never assigned
const A = "['’]"; // straight or curly apostrophe
const REQUEST = new RegExp([
  `\\b(?:can|could|may)\\s+i\\s+(?:please\\s+)?(?:work\\s+on|take|pick\\s+up|try|grab|tackle)\\b`,
  `\\bi(?:${A}d|\\s+would)\\s+(?:like|love)\\s+to\\s+(?:work\\s+on|take|pick\\s+up|try|tackle)\\b`,
  `\\bi(?:${A}ll|\\s+will)\\s+(?:work\\s+on|take|pick\\s+up)\\s+(?:this|it)\\b`,
  `\\b(?:please\\s+)?assign\\s+(?:this\\s+|it\\s+)?(?:to\\s+)?me\\b`,
  `\\bi(?:${A}m|\\s+am)\\s+interested\\b`,
].join('|'), 'i');

module.exports = async ({ github, context }) => {
  const { owner, repo } = context.repo;
  const comment = context.payload.comment;
  if (comment.user.type === 'Bot') {
    return;
  }

  const firstLine = comment.body.trim().split('\n')[0];
  const [, command = '', target] = firstLine.match(COMMAND) || [];
  const user = comment.user.login;
  const number = context.payload.issue.number;

  // re-read the issue: the event payload can be stale if two people comment at once
  const { data: issue } = await github.rest.issues.get({ owner, repo, issue_number: number });
  const assignees = issue.assignees.map((a) => a.login);
  const reply = (body) => github.rest.issues.createComment({ owner, repo, issue_number: number, body });
  const firstIssues = `https://github.com/${owner}/${repo}/labels/good%20first%20issue`;

  async function isMaintainer(login) {
    try {
      const { data } = await github.rest.repos.getCollaboratorPermissionLevel({ owner, repo, username: login });
      return data.permission === 'admin' || data.permission === 'write' || data.role_name === 'maintain';
    } catch {
      return false;
    }
  }

  switch (command.toLowerCase()) {
    case '/assign':
      return assign();
    case '/unassign':
      return unassign();
    default:
      return hint();
  }

  async function hint() {
    if (issue.state !== 'open' || assignees.includes(user) || !REQUEST.test(comment.body)
        || await isMaintainer(user)) {
      return;
    }
    // hint each person once per issue
    const marker = `<!-- flipjack:assign-hint @${user} -->`;
    const comments = await github.paginate(github.rest.issues.listComments,
      { owner, repo, issue_number: number, per_page: 100 });
    if (comments.some((c) => c.body.includes(marker))) {
      return;
    }
    return reply(`${marker}\n` + (assignees.length > 0
      ? `@${user} thanks for your interest! This issue is already assigned to @${assignees[0]}. `
        + `Have a look at the other [good first issues](${firstIssues}).`
      : `@${user} thanks for your interest! To take this issue, comment \`/assign\` `
        + `and it's assigned to you automatically.`));
  }

  async function assign() {
    if (issue.state !== 'open') {
      return reply(`@${user} this issue is closed, so it can't be assigned.`);
    }
    if (assignees.includes(user)) {
      return reply(`@${user} you're already assigned to this issue.`);
    }
    if (assignees.length > 0) {
      return reply(`@${user} this issue is already assigned to @${assignees[0]}. `
        + `Have a look at the other [good first issues](${firstIssues}).`);
    }

    if (!(await isMaintainer(user))) {
      const open = await github.paginate(github.rest.issues.listForRepo,
        { owner, repo, state: 'open', assignee: user, per_page: 100 });
      const other = open.find((i) => !i.pull_request && i.number !== number);
      if (other) {
        return reply(`@${user} you're already working on #${other.number}. To give everyone a chance, `
          + `contributors take one issue at a time. Once #${other.number} is closed (usually when its `
          + `pull request is merged), or you comment \`/unassign\` there, you can take this one.`);
      }
    }

    await github.rest.issues.addAssignees({ owner, repo, issue_number: number, assignees: [user] });

    // GitHub ignores assignees it can't assign instead of failing, so check it worked
    const { data: after } = await github.rest.issues.get({ owner, repo, issue_number: number });
    if (!after.assignees.some((a) => a.login === user)) {
      return reply(`@${user} sorry, GitHub didn't let me assign you. A maintainer will take a look.`);
    }

    return reply(`Thanks @${user}, this issue is yours! :tada:\n\n`
      + `- See [CONTRIBUTING.md](https://github.com/${owner}/${repo}/blob/main/CONTRIBUTING.md) for how to build and test.\n`
      + `- Reference this issue in your pull request (for example \`Fixes #${number}\`).\n`
      + `- Ask here if anything is unclear.\n`
      + `- If there's no activity for ${INACTIVITY_DAYS} days, the issue is released for someone else. `
      + `Comment \`/unassign\` if you can't continue.`);
  }

  async function unassign() {
    let login = user;
    if (target && target.startsWith('@') && target.slice(1) !== user) {
      if (!(await isMaintainer(user))) {
        return reply(`@${user} only maintainers can unassign someone else.`);
      }
      login = target.slice(1);
    }
    if (!assignees.includes(login)) {
      return reply(login === user
        ? `@${user} you're not assigned to this issue.`
        : `@${user} @${login} isn't assigned to this issue.`);
    }

    await github.rest.issues.removeAssignees({ owner, repo, issue_number: number, assignees: [login] });
    return reply(login === user
      ? `@${user} you've been unassigned. Thanks for letting us know! This issue is open for someone else to \`/assign\`.`
      : `@${login} has been unassigned by @${user}. This issue is open for someone else to \`/assign\`.`);
  }
};
