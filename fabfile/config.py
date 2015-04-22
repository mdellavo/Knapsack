import os

USER = "ubuntu"

DATABASE_NAME = "knapsack"

VIRTUALENV = "/virtualenv"
SITE = "/site/knapsack-backend"

PROJECT_DIR = os.path.join(os.path.dirname(__file__), "..", "backend")

NGINX_CONFIG = os.path.join(os.path.dirname(__file__), "nginx.conf")
UWSGI_CONFIG = os.path.join(os.path.dirname(__file__), "uwsgi.ini")

BASE_PACKAGES = [
    "curl",
    "python-software-properties",
    "emacs23-nox",
    "build-essential",
    "python-pip",
    "python-setuptools",
    "python-dev",
    "screen",
    "expect",
    "gdebi-core",
    "git",
    "libxml2-dev",
    "libxslt1-dev",
    "libz-dev",
    "postgresql",
    "postgresql-contrib",
    "postgresql-client",
    "nginx",
    "uwsgi",
    "uwsgi-plugin-python",
    "libpq-dev"
]

PIP_PACKAGES = [
    "transaction",
    "pyramid",
    "sqlalchemy",
    "python-gcm",
    "requests",
    "psycopg2"
]

STACK_NAME = "knapsack"

STACK = {
    "AWSTemplateFormatVersion": "2010-09-09",
    "Description": "Knapsack",

    "Outputs": {
        "AZ": {
            "Description": "Availability Zone of the newly created EC2 instance",
            "Value": {"Fn::GetAtt": ["AppServerInstance", "AvailabilityZone"]}
        },
        "InstanceId": {
            "Description": "InstanceId of the newly created EC2 instance",
            "Value": {"Ref": "AppServerInstance"}
        },
        "PublicDNS": {
            "Description": "Public DNSName of the newly created EC2 instance",
            "Value": {"Fn::GetAtt": ["AppServerInstance", "PublicDnsName"]}
        },
        "PublicIP": {
            "Description": "Public IP address of the newly created EC2 instance",
            "Value": {"Fn::GetAtt": ["AppServerInstance", "PublicIp"]}
        }
    },
    "Resources": {
        "Zone": {
            "Type": "AWS::Route53::HostedZone",
            "Properties": {
                "Name": "knapsack.quuux.org"
            }
        },

        "AppServerInstance": {
            "Type": "AWS::EC2::Instance",
            "Properties": {
                "ImageId": "ami-12793a7a",
                "InstanceType": "t2.micro",
                "KeyName": "marc",
                "NetworkInterfaces": [
                    {
                        "AssociatePublicIpAddress": "true",
                        "DeleteOnTermination": "true",
                        "DeviceIndex": "0",
                        "GroupSet": [{"Ref": "InstanceSecurityGroup"}],
                        "SubnetId": {"Ref": "Subnet"}
                    }
                ]
            },
        },

        "Hosts": {
            "Type": "AWS::Route53::RecordSet",
            "Properties": {
                "HostedZoneName": "knapsack.quuux.org.",
                "Name": "knapsack.quuux.org",
                "Type": "A",
                "TTL": "300",
                "ResourceRecords": [{"Fn::GetAtt": ["AppServerInstance", "PublicIp"]}]
            }
        },

        "AttachGateway": {
            "Type": "AWS::EC2::VPCGatewayAttachment",
            "Properties": {
                "InternetGatewayId": {"Ref": "InternetGateway"},
                "VpcId": {"Ref": "VPC"}
            }
        },
        "IPAddress": {
            "Type": "AWS::EC2::EIP",
            "DependsOn": "AttachGateway",
            "Properties": {
                "Domain": "vpc",
                "InstanceId": {"Ref": "AppServerInstance"}
            },
        },
        "InboundSSHNetworkAclEntry": {
            "Type": "AWS::EC2::NetworkAclEntry",
            "Properties": {
                "CidrBlock": "0.0.0.0/0",
                "Egress": "false",
                "NetworkAclId": {"Ref": "NetworkAcl"},
                "PortRange": {
                    "From": "22",
                    "To": "22"
                },
                "Protocol": "6",
                "RuleAction": "allow",
                "RuleNumber": "101"
            }
        },
        "InstanceSecurityGroup": {
            "Type": "AWS::EC2::SecurityGroup",
            "Properties": {
                "GroupDescription": "Enable SSH access via port 22",
                "SecurityGroupIngress": [
                    {
                        "CidrIp": "0.0.0.0/0",
                        "FromPort": "22",
                        "IpProtocol": "tcp",
                        "ToPort": "22"
                    },
                    {
                        "CidrIp": "0.0.0.0/0",
                        "FromPort": "80",
                        "IpProtocol": "tcp",
                        "ToPort": "80"
                    }
                ],
                "VpcId": {"Ref": "VPC"}
            },
        },
        "InternetGateway": {
            "Properties": {},
            "Type": "AWS::EC2::InternetGateway"
        },
        "NetworkAcl": {
            "Properties": {"VpcId": {"Ref": "VPC"}},
            "Type": "AWS::EC2::NetworkAcl"
        },
        "Route": {
            "Type": "AWS::EC2::Route",
            "DependsOn": "AttachGateway",
            "Properties": {
                "DestinationCidrBlock": "0.0.0.0/0",
                "GatewayId": {"Ref": "InternetGateway"},
                "RouteTableId": {"Ref": "RouteTable"}
            },
        },
        "RouteTable": {
            "Properties": {
                "VpcId": {"Ref": "VPC"}
            },
            "Type": "AWS::EC2::RouteTable"
        },
        "Subnet": {
            "Type": "AWS::EC2::Subnet",
            "Properties": {
                "CidrBlock": "10.0.0.0/24",
                "VpcId": {"Ref": "VPC"}
            },
        },
        "SubnetRouteTableAssociation": {
            "Type": "AWS::EC2::SubnetRouteTableAssociation",
            "Properties": {
                "RouteTableId": {"Ref": "RouteTable"},
                "SubnetId": {"Ref": "Subnet"}
            },
        },
        "VPC": {
            "Type": "AWS::EC2::VPC",
            "Properties": {
                "CidrBlock": "10.0.0.0/16",
                "EnableDnsHostnames": "true",
                "EnableDnsSupport": "true"
            },
        }
    }
}
