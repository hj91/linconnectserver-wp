'''
    LinConnect: Mirror Android notifications on Linux Desktop/Wordpress

    Copyright (C) 2013  Will Hauck
    Copyright (C) 2014  Harshad Joshi

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
'''

from __future__ import print_function

# Imports
try:
    import ConfigParser
except ImportError:
    import configparser as ConfigParser
import os
import sys
import select
import threading
import platform

import cherrypy
import subprocess
from gi.repository import Notify
import pybonjour
import shutil
import base64

if os.name != 'nt':
    import fcntl 
    import struct
    
    def get_interface_ip(ifname):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        return socket.inet_ntoa(fcntl.ioctl(s.fileno(), 0x8915, struct.pack('256s',
                                ifname[:15]))[20:24])


app_name = 'linconnect-server'
version = "2.20"

import xmlrpclib

class WP:
	def __init__(self,blog_name,user_name,user_pass,blog_id):
		self.blog_name = blog_name  #'http://localhost/wordpress/xmlrpc.php'
		self.user_name = user_name #'admin'
		self.user_pass = user_pass #'harshad'
		self.blog_id=0
		self.draft = 0
		self.simulation = 0
	
	def post(self,a,title):
		'''Contains XML-RPC procedures'''
		self.a= a # raw_input (" Enter a blog post >> ")
		self.title = title #"Sent via IM client "#>>> "+c4+" " #+str(datetitle)
		blog_content = { 'title' : str(self.title), 'description' : self.a+"\n" }
		categories = [{'categoryId' : 'Links', 'isPrimary' : 1}]
		sp = xmlrpclib.ServerProxy(self.blog_name)
		post_id = int(sp.metaWeblog.newPost(self.blog_id, self.user_name, self.user_pass, blog_content, not self.draft))
		sp.mt.setPostCategories(post_id, self.user_name, self.user_pass, categories)
		sp.mt.publishPost(post_id, self.user_name, self.user_pass)


w=WP('http://yourblog.wordpress.com/xmlrpc.php','username','password',0)


# Global Variables
_notification_header = ""
_notification_description = ""

# Configuration
script_dir = os.path.abspath(os.path.dirname(__file__))

def user_specific_location(type, file):
    dir = os.path.expanduser(os.path.join('~/.' + type, app_name))
    if not os.path.isdir(dir):
        os.makedirs(dir)
    return os.path.join(dir, file)

conf_file = user_specific_location('config', 'conf.ini')
icon_path = user_specific_location('cache', 'icon_cache.png')

old_conf_file = os.path.join(script_dir, 'conf.ini')
if os.path.isfile(old_conf_file):
    if os.path.isfile(conf_file):
        print("Both old and new config files exist: %s and %s, ignoring old one" % (old_conf_file, conf_file))
    else:
        print("Old config file %s found, moving to a new location: %s" % (old_conf_file, conf_file))
        shutil.move(old_conf_file, conf_file)
del old_conf_file

try:
    with open(conf_file):
        print("Loading conf.ini")
except IOError:
    print("Creating conf.ini")
    with open(conf_file, 'w') as text_file:
        text_file.write("""[connection]
port = 9090
enable_bonjour = 1

[other]
enable_instruction_webpage = 1
notify_timeout = 5000""")

parser = ConfigParser.ConfigParser()
parser.read(conf_file)
del conf_file

# Must append port because Java Bonjour library can't determine it
_service_name = platform.node()


class Notification(object):
    if parser.getboolean('other', 'enable_instruction_webpage') == 1:
        with open(os.path.join(script_dir, 'index.html'), 'rb') as f:
            _index_source = f.read()

        def index(self):
            return self._index_source % (version, "<br/>".join(get_local_ip()))

        index.exposed = True

    def notif(self, notificon):
        global _notification_header
        global _notification_description

        # Get icon
        try:
            os.remove(icon_path)
        except:
            print("Creating icon cache...")
        file_object = open(icon_path, "a")
        while True:
            data = notificon.file.read(8192)
            if not data:
                break
            file_object.write(str(data))
        file_object.close()

        # Ensure the notification is not a duplicate
        if (_notification_header != cherrypy.request.headers['NOTIFHEADER']) \
        or (_notification_description != cherrypy.request.headers['NOTIFDESCRIPTION']):

            # Get notification data from HTTP header
            try:
                _notification_header = base64.urlsafe_b64decode(cherrypy.request.headers['NOTIFHEADER'])
                _notification_description = base64.urlsafe_b64decode(cherrypy.request.headers['NOTIFDESCRIPTION'])
            except:
                # Maintain compatibility with old application
                _notification_header = cherrypy.request.headers['NOTIFHEADER'].replace('\x00', '').decode('iso-8859-1', 'replace').encode('utf-8')
                _notification_description = cherrypy.request.headers['NOTIFDESCRIPTION'].replace('\x00', '').decode('iso-8859-1', 'replace').encode('utf-8')

            # Send the notification
            notif = Notify.Notification.new(_notification_header, _notification_description, icon_path)
            w.post(_notification_header, _notification_description)
	    #a=open("abc.txt","a+")
	    #a.write("\n"+ _notification_header+" >> "+_notification_description+"\n")	
            if parser.has_option('other', 'notify_timeout'):
                notif.set_timeout(parser.getint('other', 'notify_timeout'))
            try:
                notif.show()
            except:
                # Workaround for org.freedesktop.DBus.Error.ServiceUnknown
                Notify.uninit()
                Notify.init("com.harshad.linconnect")
                notif.show()

        return "true"
    notif.exposed = True


def register_callback(sdRef, flags, errorCode, name, regtype, domain):
    if errorCode == pybonjour.kDNSServiceErr_NoError:
        print("Registered Bonjour service " + name)


def initialize_bonjour():
    sdRef = pybonjour.DNSServiceRegister(name=_service_name,
                                     regtype="_linconnect._tcp",
                                     port=int(parser.get('connection', 'port')),
                                     callBack=register_callback)
    try:
        try:
            while True:
                ready = select.select([sdRef], [], [])
                if sdRef in ready[0]:
                    pybonjour.DNSServiceProcessResult(sdRef)
        except KeyboardInterrupt:
            pass
    finally:
        sdRef.close()

def get_local_ip():
    ips = []
    ip = socket.gethostbyname(socket.gethostname())
    if ip.startswith("127.") and os.name != "nt":
        interfaces = [
            "eth0",
            "eth1",
            "eth2",
            "wlan0",
            "wlan1",
            "wifi0",
            "ath0",
            "ath1",
            "ppp0",
            ]
        for ifname in interfaces:
            try:
                ip = get_interface_ip(ifname)
                ips.append (ip + ":" + parser.get('connection','port'))
                break
            except IOError:
                pass
    
    return ips

# modified on 6th july 2014 _ harshad
# no need of this complex code. no spaghetti preferred

#def get_local_ip():
#    ips = []
#    for ip in subprocess.check_output("/sbin/ip address | grep -i 'inet ' | awk {'print $2'} | sed -e 's/\/[^\/]*$//'", shell=True).split("\n"):
#        if ip.__len__() > 0 and not ip.startswith("127."):
#            ips.append(ip + ":" + parser.get('connection', 'port'))
#    return ips

# Initialization
if not Notify.init("com.harshad.linconnect"):
    raise ImportError("Error initializing libnotify")

# Start Bonjour if desired
if parser.getboolean('connection', 'enable_bonjour') == 1:
    thr = threading.Thread(target=initialize_bonjour)
    thr.start()

config_instructions = "Configuration instructions at http://localhost:" + parser.get('connection', 'port')
print(config_instructions)
notif = Notify.Notification.new("Notification server started (version " + version + ")", config_instructions, "info")
notif.show()

cherrypy.server.socket_host = '0.0.0.0'
cherrypy.server.socket_port = int(parser.get('connection', 'port'))

cherrypy.quickstart(Notification())
