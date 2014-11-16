module.exports = function (grunt) {

    var DEBUG = false;
    var banner = '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n';
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        uglify: {
            options: {
                banner: banner,
                beautify: DEBUG,
                mangle: !DEBUG
            },

            dist: {
                files: {
                    'knapsackbackend/assets/js/dist/knapsack.js': [
                        'bower_components/jquery/dist/jquery.js',
                        'bower_components/bootstrap/dist/js/bootstrap.js',
                        'knapsackbackend/assets/js/*.js'
                    ]
                }
            }
        },

        jshint: {
            all: ['Gruntfile.js', 'knapsackbackend/assets/js/*.js']
        },

        cssmin: {
            add_banner: {
                options: {
                    banner: banner
                },
                files: {
                    'knapsackbackend/assets/css/dist/knapsack.css': [
                        'bower_components/bootstrap/dist/css/bootstrap.css',
                        'bower_components/bootstrap/dist/css/bootstrap-theme.css',
                        'knapsackbackend/assets/css/*.css'
                    ]
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-cssmin');

    grunt.registerTask('default', ['jshint', 'uglify', 'cssmin']);
};