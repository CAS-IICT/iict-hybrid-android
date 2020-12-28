#!/bin/bash
# 账号密码在gitlab virable中
targetDay=$(date)
mkdir ~/.ssh
echo -e "Host *\n\tStrictHostKeyChecking no\n\n" >~/.ssh/config

echo "---------------------start to deploy----------------------------"
lftp -u $USER,$PASSWD sftp://$IP:$P <<!
    mirror -R $DIST $DIR
    bye
!
echo "---------------------END----------------------------------------"

[ $? -eq 0 ] && echo "Upload to SFTP server ($IP) successfully [$targetDay]"
