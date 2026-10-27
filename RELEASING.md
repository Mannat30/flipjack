# Releasing

Releases are built from a clean clone of the release tag, signed with your GPG key, and
uploaded to Maven Central with the `release` Maven profile.

## One-time setup

- A [Central Portal](https://central.sonatype.com) account with the `org.kamranzafar`
  namespace verified, and a user token.
- The token in `~/.m2/settings.xml`, under the server id `central`:

  ```xml
  <settings>
    <servers>
      <server>
        <id>central</id>
        <username>TOKEN_USERNAME</username>
        <password>TOKEN_PASSWORD</password>
      </server>
    </servers>
  </settings>
  ```

- The signing key published to a keyserver, so Central can verify signatures:

  ```sh
  gpg --keyserver keys.openpgp.org --send-keys C7D696C81718DC31
  gpg --keyserver keyserver.ubuntu.com --send-keys C7D696C81718DC31
  ```

## Release

Replace `1.0.0` with the version being released and `1.0.1` with the next one.

1. **Set the release version** and date the changelog entry:

   ```sh
   mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
   # also update the <version> in README.md and the date in CHANGELOG.md
   git commit -am "Release 1.0.0"
   git push
   ```

   Wait for the Build workflow to pass on that commit.

2. **Tag it:**

   ```sh
   git tag -a v1.0.0 -m "1.0.0"
   git push origin v1.0.0
   ```

3. **Build, sign and upload from a clean clone** of the tag:

   ```sh
   rm -rf /tmp/flipjack-release
   git clone --branch v1.0.0 git@github.com:kamranzafar/flipjack.git /tmp/flipjack-release
   cd /tmp/flipjack-release
   mvn -P release deploy
   ```

   GPG asks for the key passphrase. The upload is validated but not published.

4. **Publish:** in the Central Portal, open the deployment, check it, and click **Publish**.
   It appears on Maven Central within about half an hour. Then create the GitHub release:

   ```sh
   gh release create v1.0.0 --title v1.0.0 --verify-tag \
       --notes-file <(sed -n '/^## \[1.0.0\]/,/^## \[/p' CHANGELOG.md | sed '1d;$d')
   ```

5. **Start the next version:**

   ```sh
   mvn versions:set -DnewVersion=1.0.1-SNAPSHOT -DgenerateBackupPoms=false
   # add an "Unreleased" section to CHANGELOG.md; leave README.md on the released version,
   # since snapshots aren't published to Maven Central
   git commit -am "Start 1.0.1-SNAPSHOT"
   git push
   ```

To publish without the manual step in the Portal, add `-Dcentral.autoPublish=true` to the
`deploy` command.
