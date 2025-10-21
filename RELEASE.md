# Automated release workflow
Release is performed with GitHub Actions workflow [release.yml](https://github.com/eclipse/che/actions/workflows/release.yml).
The release will perform the build of Maven Artifacts, build and push of all nesessary docker images, and bumping up development version.

[make-release.sh](https://github.com/eclipse/che/blob/master/make-release.sh) is the script that can be used for standalone release outside of GitHub Actions. However, ensure that all environment variables are set in place before invoking `./make-release.sh`, similarly to how it is outlined in [release.yml](https://github.com/eclipse/che/actions/workflows/release.yml):

REBUILD_FROM_EXISTING_TAGS - if `true`, release will not create new tag, but instead checkout to existing one. Use this to rerun failed attempts, without having to recreate the tag.
BUILD_AND_PUSH_IMAGES - if `true`, will build all asociated images in [dockerfiles](https://github.com/eclipse/che/tree/master/dockerfiles) directory. Set `false`, if this step needs to be skipped.
BUMP_NEXT_VERSION - if `true`, will increase the development versions in main and bugfix branches. Set false, if this step needs to be skipped
