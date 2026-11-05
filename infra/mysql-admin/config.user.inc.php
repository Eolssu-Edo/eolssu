<?php

$cfg['AllowArbitraryServer'] = false;
$cfg['LoginCookieValidity'] = 900;
$cfg['Servers'][$i]['auth_type'] = 'cookie';
$cfg['Servers'][$i]['AllowRoot'] = false;
$cfg['Servers'][$i]['AllowNoPassword'] = false;
$cfg['Servers'][$i]['only_db'] = ['eolssu'];
$cfg['Servers'][$i]['AllowDeny'] = [
    'order' => 'explicit',
    'rules' => ['allow eolssu_web_read from all'],
];
