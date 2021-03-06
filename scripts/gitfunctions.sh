#!/bin/bash
#
# Convenient functions for use on Nuxeo projects version controlled under Git.
#
# (C) Copyright 2009-2016 Nuxeo SA (http://nuxeo.com/) and contributors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Contributors:
#   Julien Carsique, Kevin Leturc
#

# Usage: _retrieve_modules <result>
#   result: name of the variable which will receive the list of Maven modules of the current POM
function _retrieve_modules {
  local __resultvar=$1
  local _list=LIST_$(echo $(basename $PWD)|tr '-' '_'|tr '[:lower:]' '[:upper:]')
  local _listval=$(eval "echo \$$_list")
  if [ -z "$_listval" ]; then
    [ "$quiet" != true ] && echo "Modules list calculated from POM"
    local _result="$(grep '<module>' pom.xml |cut -d ">" -f 2 |cut -d "<" -f 1|sort|uniq|tr '\n' ' ')"
  else
    [ "$quiet" != true ] && echo "Modules list set from environment variable: $_list"
    local _result="$_listval"
  fi
  eval "$__resultvar='$_result'"
}

# Usage: _execute_on_modules <function>
#   function: a Shell function to execute on each module returned by _execute_on_modules
function _execute_on_modules {
  local f=$1
  local _modules=""
  _retrieve_modules _modules
  [ "$quiet" != true ] && echo -e "Execute on modules: $_modules\n"
  for dir in $(echo $_modules|tr '\n' ' '); do
    if [ -d $dir ]; then
      ( cd $dir
        $f $dir
      )
    else
      echo "[$dir]"
      echo -e "ERROR: No directory named $dir\n" >&2
    fi
  done
}

gitf() {
  # Parse options
  local OPTIND=1 # getopts reset
  local recurse=false
  local quiet=false
  local opt
  while getopts "qrh?" opt; do
    case "$opt" in
    q) quiet=true
      ;;
    r) recurse=true
      ;;
    h|\?)
      echo -e "Usage: gitf [-r] [-q] [-h|-?] <[--] Git instructions>
Recursively executes the given Git command on the current and its direct children Git repositories.\n
\t-h,-?\tShow this help message and exit.
\t-q\tQuiet mode (default is verbose).
\t-r\tThe recurse will continue on all children Git repositories.
\t--\tOptional separator between the options and the instructions which may start with a dash."
      return
      ;;
    esac
  done
  shift $((OPTIND-1))
  [ "$1" = "--" ] && shift
  local git_args=$@

  function git_command {
    local dir
    if [ -e .git ]; then
      dir=$(basename $PWD)
      echo "[$dir]"
      [ "$quiet" != true ] && echo "\$> git $git_args"
      (eval git $git_args) || true
      echo
    fi
    for dir in $(ls -d */); do
      dir=${dir%%/};
      if [ -e "$dir"/.git ]; then
        ( cd "$dir"
          if [ "$recurse" = true ]; then
              git_command
          else
            echo "[$dir]"
            [ "$quiet" != true ] && echo "\$> git $git_args"
            (eval git $git_args) || true
            echo
          fi
        )
      fi
    done
  }
  git_command
}

gitfa() {
  # Parse options
  local OPTIND=1 # getopts reset
  local quiet=false
  local opt
  while getopts "qh?" opt; do
    case "$opt" in
    q) quiet=true
      ;;
    h|\?)
      echo -e "Usage: gitfa [-q] [-h|-?] <[--] Git instructions>
Recursively executes the given Git command on the current, its direct children Git repositories \
and browse the child Maven modules of \$PARENT_MODULES directories.\n
\t-h,-?\tShow this help message and exit.
\t-q\tQuiet mode (default is verbose).
\t--\tOptional separator between the options and the instructions which may start with a dash.
\tPARENT_MODULES Environment variable. Defaults to 'addons addons-core' if not set.
\tLIST_<parent module> Environment variable that can be set to restrict the children modules to a fixed list. \
For instance: LIST_ADDONS_CORE=\"nuxeo-core-storage-marklogic\" or LIST_ADDONS=\"nuxeo-shell nuxeo-quota\"."
      return
      ;;
    esac
  done
  shift $((OPTIND-1))
  [ "$1" = "--" ] && shift
  local git_args=$@

  local parent_modules=${PARENT_MODULES:-"addons addons-core"}
  function git_command {
    if [ -e .git ]; then
      echo "[$1]"
      [ "$quiet" != true ] && echo "\$> git $git_args"
      (eval git $git_args) || true
      echo
    fi
  }
  local dir=$(basename $PWD)
  git_command $dir
  # Detect if we are at Nuxeo Platform repository's root
  if [ -d "nuxeo-common" ]; then
    local dir
    for dir in $(ls -d */); do
      dir=${dir%%/};
      ( cd "$dir"
        git_command "$dir"
        case " $parent_modules " in
          *" $dir "*) _execute_on_modules "git_command"
            ;;
        esac
      )
    done
  else
    case " $parent_modules " in
      *" $dir "*) _execute_on_modules "git_command"
        ;;
    esac
  fi
}

shr() {
  # Parse options
  local OPTIND=1 # getopts reset
  local quiet=false
  local parent_modules="none"
  local opt
  while getopts "qah?" opt; do
    case "$opt" in
    q) quiet=true
      ;;
    a) parent_modules=${PARENT_MODULES:-"addons addons-core"}
      ;;
    h|\?)
      echo -e "Usage: shr [-a] [-q] [-h|-?] <[--] Shell instructions>
Recursively executes the given Shell command on the current and its direct children Git repositories.\n
\t-h,-?\tShow this help message and exit.
\t-a\tThe recurse will continue on the child Maven modules of \$PARENT_MODULES directories.
\t-q\tQuiet mode (default is verbose).
\t--\tOptional separator between the options and the instructions which may start with a dash.
\tPARENT_MODULES Environment variable. Defaults to 'addons addons-core' if not set.
\tLIST_<parent module> Environment variable that can be set to restrict the children modules to a fixed list. \
For instance: LIST_ADDONS_CORE=\"nuxeo-core-storage-marklogic\" or LIST_ADDONS=\"nuxeo-shell nuxeo-quota\"."
      return
      ;;
    esac
  done
  shift $((OPTIND-1))
  [ "$1" = "--" ] && shift
  local sh_args=$@

  function shell_command {
    if [ -e .git ]; then
      echo "[$1]"
      [ "$quiet" != true ] && echo "\$> $sh_args"
      (eval $sh_args) || true
      echo
    fi
  }
  local dir=$(basename $PWD)
  shell_command $dir
  # Detect if we are at Nuxeo Platform repository's root
  if [ -d "nuxeo-common" ]; then
    for dir in $(ls -d */); do
      dir=${dir%%/};
      ( cd "$dir"
        shell_command "$dir"
        if [[ "$parent_modules" =~ (^|[[:space:]])"$dir"($|[[:space:]]) ]]; then
          _execute_on_modules "shell_command"
        fi
      )
    done
  elif [[ "$parent_modules" =~ (^|[[:space:]])"$dir"($|[[:space:]]) ]]; then
    _execute_on_modules "shell_command"
  fi
}
