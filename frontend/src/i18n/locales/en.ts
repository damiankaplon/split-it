import type {Translations} from './pl'

const en: Translations = {
  language: {
    label: 'Language',
    pl: 'Polski',
    en: 'English',
  },
  signIn: {
    title: 'Sign in to SplitIt!',
    subtitle: 'Track shared expenses and see who owes what.',
    button: 'Continue with Keycloak',
    hint: "You'll be redirected to the sign-in page and brought back here.",
    keycloakUnavailable: 'Could not reach Keycloak. Is it running?',
  },
  header: {
    accountMenu: 'Account menu',
    signedInAs: 'Signed in as',
    signOut: 'Sign out',
  },
  projects: {
    title: 'Projects',
    subtitle: 'Projects you own or have joined.',
    loading: 'Loading projects…',
    loadError: "Couldn't load projects",
    empty: "You're not part of any project yet. Create one to get started.",
    owner: 'Owner',
    member: 'Member',
  },
  newProject: {
    button: 'New project',
    title: 'New project',
    description: 'You can invite people once the project is created.',
    nameLabel: 'Name',
    namePlaceholder: 'e.g. Zakopane trip',
    cancel: 'Cancel',
    submit: 'Create project',
    error: "Couldn't create the project. Please try again.",
  },
  invite: {
    button: 'Invite',
    title: 'Invite to {{project}}',
    description: 'Each link lets one person join after signing in.',
    linkLabel: 'Invite link',
    creating: 'Creating link…',
    copy: 'Copy',
    copied: 'Copied',
    singleUse: 'Once used, the link stops working.',
    error: "Couldn't create an invite link.",
    newLink: 'New link',
    members: 'Members',
    loadingMembers: 'Loading…',
    you: '(you)',
    done: 'Done',
  },
  join: {
    checking: 'Checking invite…',
    unavailableTitle: 'Invite not available',
    invalid: 'This invite link is invalid, has expired, or has already been used.',
    askOwner: 'Ask the project owner for a new one.',
    goToProjects: 'Go to projects',
    invitedTo: "You've been invited to join",
    signedInInfo:
        "You're signed in as {{username}}. After joining you'll see every expense in this project and share new ones.",
    notNow: 'Not now',
    join: 'Join project',
  },
}

export default en
